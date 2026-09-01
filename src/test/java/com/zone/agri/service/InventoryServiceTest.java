package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.request.inventory.InventoryQCRequest;
import com.zone.agri.dto.response.inventory.InventoryReceiptResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryNoteDetail;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryNoteDetailRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.PurchaseRequestRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryNoteRepository noteRepository;
    @Mock
    private InventoryNoteDetailRepository noteDetailRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private BackorderService backorderService;
    @Mock
    private WarehouseContext warehouseContext;
    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;
    @Mock
    private InventoryCheckGuardService inventoryCheckGuardService;
    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void approveReceipt_shouldOnlyMovePendingReceiptToApproved() {
        InventoryNote note = receipt(InventoryNoteStatus.PENDING, new ArrayList<>());

        when(noteRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any(InventoryNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = inventoryService.approveReceipt(10L);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(note.getStatus()).isEqualTo(InventoryNoteStatus.APPROVED);
        verify(inventoryRepository, never()).findExactBatchWithLock(any(), any(), any(), any(), any());
        verify(transactionRepository, never()).save(any(InventoryTransaction.class));
    }

    @Test
    void completeReceipt_shouldStockAcceptedAndRejectedButChargeAcceptedOnly() {
        ProductVariant variant = variant();
        InventoryNoteDetail detail = InventoryNoteDetail.builder()
                .productVariant(variant)
                .quantity(10)
                .quantityRequested(10)
                .price(new BigDecimal("100"))
                .batchNumber("LOT-1")
                .inventoryNote(null)
                .build();
        InventoryNote note = receipt(InventoryNoteStatus.APPROVED, new ArrayList<>(List.of(detail)));
        detail.setInventoryNote(note);

        Inventory inventory = Inventory.builder()
                .branch(note.getBranch())
                .productVariant(variant)
                .batchNumber("LOT-1")
                .importPrice(new BigDecimal("100"))
                .quantity(5)
                .defectiveQuantity(1)
                .build();

        InventoryQCRequest request = InventoryQCRequest.builder()
                .items(List.of(InventoryQCRequest.ItemQCRequest.builder()
                        .productCode("SKU-1")
                        .quantityDelivered(10)
                        .quantityReal(7)
                        .quantityRejected(3)
                        .lotNumber("LOT-1")
                        .build()))
                .build();

        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(inventoryRepository.findExactBatchWithLock(eq(note.getBranch()), eq(variant), eq("LOT-1"), eq(new BigDecimal("100")), isNull()))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noteRepository.save(any(InventoryNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = inventoryService.completeReceipt(10L, request);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("700");
        assertThat(response.getDebtAmount()).isEqualByComparingTo("700");
        assertThat(inventory.getQuantity()).isEqualTo(12);
        assertThat(inventory.getDefectiveQuantity()).isEqualTo(4);
        verify(backorderService).fulfillBackordersOnStockReceive(1L, 100L, 7);

        ArgumentCaptor<InventoryTransaction> transactionCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository, times(2)).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getAllValues())
                .extracting(InventoryTransaction::getType, InventoryTransaction::getQuantityChange,
                        InventoryTransaction::getNewBalance, InventoryTransaction::getReason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TransactionType.IMPORT, 7, 13,
                                "Nhap kho hang dat QC (Phieu: PN-10)"),
                        org.assertj.core.groups.Tuple.tuple(TransactionType.IMPORT, 3, 16,
                                "Nhap kho hang loi QC (Phieu: PN-10)"));
    }

    @Test
    void completeReceipt_shouldLogRejectedStockWhenAllItemsFailQc() {
        ProductVariant variant = variant();
        InventoryNoteDetail detail = InventoryNoteDetail.builder()
                .productVariant(variant)
                .quantity(10)
                .quantityRequested(10)
                .price(new BigDecimal("100"))
                .batchNumber("LOT-1")
                .inventoryNote(null)
                .build();
        InventoryNote note = receipt(InventoryNoteStatus.APPROVED, new ArrayList<>(List.of(detail)));
        detail.setInventoryNote(note);

        Inventory inventory = Inventory.builder()
                .branch(note.getBranch())
                .productVariant(variant)
                .batchNumber("LOT-1")
                .importPrice(new BigDecimal("100"))
                .quantity(5)
                .defectiveQuantity(1)
                .build();

        InventoryQCRequest request = InventoryQCRequest.builder()
                .items(List.of(InventoryQCRequest.ItemQCRequest.builder()
                        .productCode("SKU-1")
                        .quantityDelivered(10)
                        .quantityReal(0)
                        .quantityRejected(10)
                        .lotNumber("LOT-1")
                        .build()))
                .build();

        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(inventoryRepository.findExactBatchWithLock(eq(note.getBranch()), eq(variant), eq("LOT-1"), eq(new BigDecimal("100")), isNull()))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noteRepository.save(any(InventoryNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = inventoryService.completeReceipt(10L, request);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(inventory.getQuantity()).isEqualTo(5);
        assertThat(inventory.getDefectiveQuantity()).isEqualTo(11);
        verify(backorderService, never()).fulfillBackordersOnStockReceive(any(), any(), anyInt());

        ArgumentCaptor<InventoryTransaction> transactionCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        InventoryTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getType()).isEqualTo(TransactionType.IMPORT);
        assertThat(transaction.getQuantityChange()).isEqualTo(10);
        assertThat(transaction.getNewBalance()).isEqualTo(16);
        assertThat(transaction.getReferenceCode()).isEqualTo("PN-10");
        assertThat(transaction.getReason()).isEqualTo("Nhap kho hang loi QC (Phieu: PN-10)");
    }

    @Test
    void completeReceipt_shouldRejectManagedCategoryWithoutExpiryDate() {
        ProductVariant variant = managedVariant("Thuoc thuy san");
        InventoryNoteDetail detail = InventoryNoteDetail.builder()
                .productVariant(variant)
                .quantity(5)
                .quantityRequested(5)
                .price(new BigDecimal("100"))
                .batchNumber("LOT-MED-1")
                .build();
        InventoryNote note = receipt(InventoryNoteStatus.APPROVED, new ArrayList<>(List.of(detail)));
        detail.setInventoryNote(note);

        InventoryQCRequest request = InventoryQCRequest.builder()
                .items(List.of(InventoryQCRequest.ItemQCRequest.builder()
                        .productCode("SKU-MED")
                        .quantityDelivered(5)
                        .quantityReal(5)
                        .quantityRejected(0)
                        .lotNumber("LOT-MED-1")
                        .build()))
                .build();

        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> inventoryService.completeReceipt(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hạn sử dụng");

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    void completeReceipt_shouldSeparateSameBatchNumberByExpiryDate() {
        ProductVariant variant = variant();
        InventoryNoteDetail detail = InventoryNoteDetail.builder()
                .productVariant(variant)
                .quantity(3)
                .quantityRequested(3)
                .price(new BigDecimal("100"))
                .batchNumber("LOT-1")
                .build();
        InventoryNote note = receipt(InventoryNoteStatus.APPROVED, new ArrayList<>(List.of(detail)));
        detail.setInventoryNote(note);
        LocalDate expiryDate = LocalDate.now().plusMonths(6);

        InventoryQCRequest request = InventoryQCRequest.builder()
                .items(List.of(InventoryQCRequest.ItemQCRequest.builder()
                        .productCode("SKU-1")
                        .quantityDelivered(3)
                        .quantityReal(3)
                        .quantityRejected(0)
                        .lotNumber("LOT-1")
                        .expiryDate(expiryDate.toString())
                        .build()))
                .build();

        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(inventoryRepository.findExactBatchWithLock(
                eq(note.getBranch()),
                eq(variant),
                eq("LOT-1"),
                eq(new BigDecimal("100")),
                eq(expiryDate.atStartOfDay())))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(noteRepository.save(any(InventoryNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.completeReceipt(10L, request);

        ArgumentCaptor<Inventory> inventoryCaptor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository, times(2)).save(inventoryCaptor.capture());
        assertThat(inventoryCaptor.getAllValues().get(0).getExpiryDate()).isEqualTo(expiryDate.atStartOfDay());
    }

    private InventoryNote receipt(InventoryNoteStatus status, List<InventoryNoteDetail> details) {
        return InventoryNote.builder()
                .id(10L)
                .code("PN-10")
                .type(InventoryNoteType.IMPORT)
                .status(status)
                .branch(Branch.builder().id(1L).name("Kho Tong").build())
                .supplier(Supplier.builder().id(2L).code("NCC-1").name("NCC 1").build())
                .paymentAmount(BigDecimal.ZERO)
                .details(details)
                .build();
    }

    private ProductVariant variant() {
        return ProductVariant.builder()
                .id(100L)
                .sku("SKU-1")
                .product(Product.builder().id(200L).name("San pham 1").build())
                .build();
    }

    private ProductVariant managedVariant(String categoryName) {
        return ProductVariant.builder()
                .id(101L)
                .sku("SKU-MED")
                .product(Product.builder()
                        .id(201L)
                        .name("San pham can HSD")
                        .category(Category.builder().id(301L).name(categoryName).build())
                        .build())
                .build();
    }
}

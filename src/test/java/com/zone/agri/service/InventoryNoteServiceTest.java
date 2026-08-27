package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.zone.agri.common.WarehouseContext;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryNoteDetail;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryNoteDetailRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryNoteServiceTest {

    @Mock
    private InventoryNoteRepository inventoryNoteRepository;
    @Mock
    private InventoryNoteDetailRepository inventoryNoteDetailRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private BackorderService backorderService;
    @Mock
    private InventoryCheckGuardService inventoryCheckGuardService;
    @Mock
    private WarehouseContext warehouseContext;

    @InjectMocks
    private InventoryNoteService inventoryNoteService;

    @Test
    void completeExportCommand_disposal_shouldDeductDefectiveStockAndWriteDamagedLedger() {
        Branch warehouse = Branch.builder()
                .id(1L)
                .name("Kho Tong")
                .branchType("WAREHOUSE")
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L)
                .sku("SKU-10")
                .build();
        InventoryNote note = InventoryNote.builder()
                .id(99L)
                .code("LXH-99")
                .type(InventoryNoteType.EXPORT)
                .status(InventoryNoteStatus.APPROVED)
                .reason("Loai: DISPOSAL | Ly do: Hang hong can tieu huy")
                .branch(warehouse)
                .details(List.of(InventoryNoteDetail.builder()
                        .productVariant(variant)
                        .quantityRequested(3)
                        .batchNumber("LOT-1")
                        .price(new BigDecimal("100"))
                        .build()))
                .build();
        Inventory batch = Inventory.builder()
                .id(7L)
                .branch(warehouse)
                .productVariant(variant)
                .batchNumber("LOT-1")
                .quantity(5)
                .defectiveQuantity(4)
                .build();

        when(inventoryNoteRepository.findByIdWithDetails(99L)).thenReturn(Optional.of(note));
        when(inventoryRepository.findExactBatchListByNumber(1L, 10L, "LOT-1")).thenReturn(List.of(batch));
        when(inventoryNoteRepository.save(any(InventoryNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryNoteService.completeExportCommand(99L);

        assertThat(batch.getDefectiveQuantity()).isEqualTo(1);
        assertThat(note.getDebtAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        ArgumentCaptor<InventoryTransaction> transactionCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        InventoryTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getType()).isEqualTo(TransactionType.DAMAGED);
        assertThat(transaction.getQuantityChange()).isEqualTo(-3);
        assertThat(transaction.getNewBalance()).isEqualTo(6);
        assertThat(transaction.getReferenceCode()).isEqualTo("LXH-99");
    }
}

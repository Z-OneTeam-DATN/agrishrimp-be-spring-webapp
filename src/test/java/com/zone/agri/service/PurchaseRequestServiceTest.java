package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.request.purchase.PurchaseRequestCreateRequest;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.PurchaseRequestItemRepository;
import com.zone.agri.repository.PurchaseRequestRepository;
import com.zone.agri.repository.SupplierProductCatalogRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.repository.SubOrderRepository;
import com.zone.agri.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PurchaseRequestServiceTest {

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;

    @Mock
    private PurchaseRequestItemRepository purchaseRequestItemRepository;

    @Mock
    private InventoryNoteRepository inventoryNoteRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierProductCatalogRepository supplierProductCatalogRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WarehouseContext warehouseContext;

    @Mock
    private EmailService emailService;

    @Mock
    private SubOrderRepository subOrderRepository;

    @Mock
    private InventoryTransferService inventoryTransferService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PurchaseRequestService purchaseRequestService;

    @Test
    void createRequest_shouldThrowBadRequestExceptionWhenSupplierIsInactive() {
        String supplierCode = "NCC-INACTIVE";
        Supplier supplier = Supplier.builder()
                .id(1L)
                .code(supplierCode)
                .status(SupplierStatus.INACTIVE)
                .build();

        when(supplierRepository.findByCode(supplierCode)).thenReturn(Optional.of(supplier));

        PurchaseRequestCreateRequest request = new PurchaseRequestCreateRequest();
        request.setSupplierCode(supplierCode);

        assertThatThrownBy(() -> purchaseRequestService.createRequest(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nhà cung cấp đang tạm ngừng giao dịch. Không thể tạo phiếu yêu cầu mua.");
    }

    @Test
    void createRequest_shouldThrowBadRequestExceptionWhenVariantNotAvailableInSupplierCatalog() {
        String supplierCode = "NCC-ACTIVE";
        Supplier supplier = Supplier.builder()
                .id(1L)
                .code(supplierCode)
                .status(SupplierStatus.ACTIVE)
                .build();

        Branch branch = Branch.builder().id(2L).branchType("WAREHOUSE").name("Kho Tong").build();
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();

        when(supplierRepository.findByCode(supplierCode)).thenReturn(Optional.of(supplier));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(branchRepository.findAll()).thenReturn(List.of(branch));
        when(productVariantRepository.findBySku("SKU-10")).thenReturn(Optional.of(variant));
        
        // Mock catalog check returns false (not available)
        when(supplierProductCatalogRepository.existsAvailableBySupplierIdAndProductVariantId(1L, 10L))
                .thenReturn(false);

        PurchaseRequestCreateRequest request = new PurchaseRequestCreateRequest();
        request.setSupplierCode(supplierCode);
        request.setBranchId(2L);
        
        PurchaseRequestCreateRequest.ItemRequest itemReq = new PurchaseRequestCreateRequest.ItemRequest();
        itemReq.setProductCode("SKU-10");
        itemReq.setRequestedQty(5);
        request.setItems(List.of(itemReq));

        assertThatThrownBy(() -> purchaseRequestService.createRequest(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SKU SKU-10 không nằm trong catalog đang bán của nhà cung cấp");
    }

    @Test
    void createAutomaticReplenishmentRequestResultForSubOrder_returnsBlockedItemWhenSupplierCatalogMissing() {
        Branch destinationBranch = Branch.builder().id(3L).branchType("STORE").name("Chi Nhanh A").build();
        Branch warehouse = Branch.builder().id(1L).branchType("WAREHOUSE").name("Kho Tong").build();
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-Z").build();
        Order order = Order.builder().id(1000L).code("ORD-001").status(OrderStatus.AWAITING_REPLENISHMENT).build();
        SubOrder subOrder = SubOrder.builder()
                .id(34L)
                .order(order)
                .branch(destinationBranch)
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .items(List.of(SubOrderItem.builder()
                        .productVariant(variant)
                        .quantity(2)
                        .allocatedQuantity(0)
                        .missingQuantity(2)
                        .build()))
                .build();

        when(subOrderRepository.findByIdWithItems(34L)).thenReturn(Optional.of(subOrder));
        when(purchaseRequestRepository.findAutoReplenishmentRequestsByLinkedSubOrderIdExcludingStatuses(any(), any()))
                .thenReturn(List.of());
        when(branchRepository.findAll()).thenReturn(List.of(warehouse, destinationBranch));
        when(supplierProductCatalogRepository.findByProductVariantIdInAndStatus(any(), any()))
                .thenReturn(List.of());

        PurchaseRequestService.AutomaticReplenishmentRequestResult result =
                purchaseRequestService.createAutomaticReplenishmentRequestResultForSubOrder(
                        subOrder,
                        Map.of(variant.getId(), 2));

        assertThat(result.purchaseRequests()).isEmpty();
        assertThat(result.blockedQuantitiesByVariantId()).containsEntry(variant.getId(), 2);
        assertThat(result.blockedMessagesByVariantId().get(variant.getId())).contains("SKU-Z");
    }
}

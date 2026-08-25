package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.request.purchase.PurchaseRequestCreateRequest;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.PurchaseRequest;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.SupplierProductCatalog;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PurchaseRequestStatus;
import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.Forbidden;
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWarehouseManager(Branch branch) {
        String email = "warehouse.manager@example.com";
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email,
                "N/A",
                List.of(new SimpleGrantedAuthority("PURCHASE_REQUEST_CREATE"))));

        User user = User.builder()
                .email(email)
                .branch(branch)
                .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
    }

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

        Branch branch = Branch.builder().id(2L).branchType("WAREHOUSE").name("Chi Nhanh 2").build();
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();

        authenticateWarehouseManager(branch);
        when(supplierRepository.findByCode(supplierCode)).thenReturn(Optional.of(supplier));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(purchaseRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVariantRepository.findBySku("SKU-10")).thenReturn(Optional.of(variant));
        
        when(supplierProductCatalogRepository.findAvailableBySupplierIdAndProductVariantId(1L, 10L))
                .thenReturn(Optional.empty());

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
    void createRequest_shouldUseSupplierCatalogPriceInsteadOfRequestPrice() {
        String supplierCode = "NCC-ACTIVE";
        Supplier supplier = Supplier.builder()
                .id(1L)
                .code(supplierCode)
                .status(SupplierStatus.ACTIVE)
                .build();

        Branch branch = Branch.builder().id(2L).branchType("WAREHOUSE").name("Chi Nhanh 2").build();
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();
        SupplierProductCatalog catalog = SupplierProductCatalog.builder()
                .supplier(supplier)
                .productVariant(variant)
                .status(SupplierProductCatalogStatus.AVAILABLE)
                .price(new BigDecimal("123000"))
                .build();

        authenticateWarehouseManager(branch);
        when(supplierRepository.findByCode(supplierCode)).thenReturn(Optional.of(supplier));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));
        when(purchaseRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVariantRepository.findBySku("SKU-10")).thenReturn(Optional.of(variant));
        when(supplierProductCatalogRepository.findAvailableBySupplierIdAndProductVariantId(1L, 10L))
                .thenReturn(Optional.of(catalog));
        when(inventoryNoteRepository.findGoodsReceiptsByPurchaseRequestId(any())).thenReturn(List.of());

        PurchaseRequestCreateRequest request = new PurchaseRequestCreateRequest();
        request.setSupplierCode(supplierCode);
        request.setBranchId(2L);

        PurchaseRequestCreateRequest.ItemRequest itemReq = new PurchaseRequestCreateRequest.ItemRequest();
        itemReq.setProductCode("SKU-10");
        itemReq.setRequestedQty(2);
        itemReq.setUnitPrice(BigDecimal.ONE);
        request.setItems(List.of(itemReq));

        var response = purchaseRequestService.createRequest(request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("123000");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("246000");
    }

    @Test
    void createRequest_shouldRejectWarehouseUserCreatingForAnotherWarehouse() {
        String supplierCode = "NCC-ACTIVE";
        Supplier supplier = Supplier.builder()
                .id(1L)
                .code(supplierCode)
                .status(SupplierStatus.ACTIVE)
                .build();

        Branch ownWarehouse = Branch.builder().id(2L).branchType("WAREHOUSE").name("Chi Nhanh 2").build();
        Branch anotherWarehouse = Branch.builder().id(3L).branchType("WAREHOUSE").name("Chi Nhanh 3").build();

        authenticateWarehouseManager(ownWarehouse);
        when(supplierRepository.findByCode(supplierCode)).thenReturn(Optional.of(supplier));
        when(branchRepository.findById(3L)).thenReturn(Optional.of(anotherWarehouse));

        PurchaseRequestCreateRequest request = new PurchaseRequestCreateRequest();
        request.setSupplierCode(supplierCode);
        request.setBranchId(3L);

        assertThatThrownBy(() -> purchaseRequestService.createRequest(request))
                .isInstanceOf(Forbidden.class)
                .hasMessageContaining("Chi duoc tao phieu yeu cau nhap NCC cho kho tong minh quan ly.");
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

    @Test
    void createAutomaticReplenishmentRequestResultForSubOrder_keepsApprovedPrWhenResendConfigMissing() {
        Branch warehouse = Branch.builder().id(1L).branchType("WAREHOUSE").name("Kho Tong").build();
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-WH").build();
        Supplier supplier = Supplier.builder()
                .id(20L)
                .code("NCC-WH")
                .email("ncc@example.com")
                .status(SupplierStatus.ACTIVE)
                .build();
        SupplierProductCatalog catalog = SupplierProductCatalog.builder()
                .productVariant(variant)
                .supplier(supplier)
                .price(new BigDecimal("100000"))
                .status(SupplierProductCatalogStatus.AVAILABLE)
                .build();
        Order order = Order.builder().id(1000L).code("ORD-WH-001").status(OrderStatus.AWAITING_REPLENISHMENT).build();
        SubOrder subOrder = SubOrder.builder()
                .id(34L)
                .order(order)
                .branch(warehouse)
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .items(List.of(SubOrderItem.builder()
                        .productVariant(variant)
                        .quantity(3)
                        .allocatedQuantity(0)
                        .missingQuantity(3)
                        .build()))
                .build();

        when(subOrderRepository.findByIdWithItems(34L)).thenReturn(Optional.of(subOrder));
        when(purchaseRequestRepository.findAutoReplenishmentRequestsByLinkedSubOrderIdExcludingStatuses(any(), any()))
                .thenReturn(List.of());
        when(inventoryTransferService.resolveProcurementWarehouseForDestinationBranch(warehouse))
                .thenReturn(warehouse);
        when(supplierProductCatalogRepository.findByProductVariantIdInAndStatus(any(), any()))
                .thenReturn(List.of(catalog));
        when(purchaseRequestRepository.save(any(PurchaseRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByEmail("auto@example.com")).thenReturn(Optional.empty());
        doThrow(new BadRequestException("Chua cau hinh RESEND_API_KEY"))
                .when(emailService)
                .sendPurchaseRequestToSupplier(any(PurchaseRequest.class));

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "auto@example.com",
                "secret",
                List.of(new SimpleGrantedAuthority("PURCHASE_REQUEST_APPROVE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            PurchaseRequestService.AutomaticReplenishmentRequestResult result =
                    purchaseRequestService.createAutomaticReplenishmentRequestResultForSubOrder(
                            subOrder,
                            Map.of(variant.getId(), 3));

            assertThat(result.purchaseRequests()).hasSize(1);
            PurchaseRequest purchaseRequest = result.purchaseRequests().get(0);
            assertThat(purchaseRequest.getStatus()).isEqualTo(PurchaseRequestStatus.APPROVED);
            assertThat(purchaseRequest.getSentToSupplierAt()).isNull();
            assertThat(purchaseRequest.getBranch()).isEqualTo(warehouse);
            assertThat(purchaseRequest.getLinkedDestinationBranchId()).isEqualTo(warehouse.getId());
            assertThat(purchaseRequest.getNote()).contains("RESEND_API_KEY");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

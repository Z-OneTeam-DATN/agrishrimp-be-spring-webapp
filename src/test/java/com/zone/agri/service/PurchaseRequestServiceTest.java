package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.request.purchase.PurchaseRequestCreateRequest;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.PurchaseRequestItemRepository;
import com.zone.agri.repository.PurchaseRequestRepository;
import com.zone.agri.repository.SupplierProductCatalogRepository;
import com.zone.agri.repository.SupplierRepository;
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
                .hasMessageContaining("SKU SKU-10 is not available in supplier catalog");
    }
}

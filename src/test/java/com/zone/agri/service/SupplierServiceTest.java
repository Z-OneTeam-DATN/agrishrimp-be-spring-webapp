package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.dto.response.supplier.SupplierResponse;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.SupplierProductCatalog;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.SupplierProductCatalogRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private InventoryNoteRepository inventoryNoteRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private SupplierProductCatalogRepository supplierProductCatalogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SupplierService supplierService;


    @Test
    void getSupplierById_shouldStillReturnDetailWhenDuplicateWarningLookupFails() {
        Supplier supplier = Supplier.builder()
                .id(7L)
                .code("NCC-7")
                .name("Cong ty test")
                .contactName("Nguoi lien he")
                .phone("0909000000")
                .taxCode("1234567890")
                .provinceId("79")
                .addressDetail("Dia chi test")
                .status(SupplierStatus.ACTIVE)
                .build();

        when(supplierRepository.findById(7L)).thenReturn(Optional.of(supplier));
        when(supplierProductCatalogRepository.findAllBySupplierId(7L)).thenReturn(List.of());
        when(supplierRepository.findFirstByPhoneAndIdNot(eq("0909000000"), eq(7L)))
                .thenReturn(Optional.of(Supplier.builder().code("NCC-8").name("Trùng SĐT").build()));
        SupplierResponse response = supplierService.getSupplierById(7L);

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getCode()).isEqualTo("NCC-7");
        assertThat(response.getWarnings())
                .extracting(warning -> warning.getCode())
                .contains("DUPLICATE_PHONE");
    }

    @Test
    void saveProductCatalog_shouldThrowConflictExceptionWhenVersionMismatch() {
        Long supplierId = 1L;
        Supplier supplier = Supplier.builder().id(supplierId).status(SupplierStatus.ACTIVE).build();
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();

        SupplierProductCatalog existingCatalog = new SupplierProductCatalog();
        existingCatalog.setSupplier(supplier);
        existingCatalog.setProductVariant(variant);
        existingCatalog.setStatus(com.zone.agri.entity.enums.SupplierProductCatalogStatus.AVAILABLE);
        existingCatalog.setVersion(2); // DB version is 2

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierProductCatalogRepository.findAllBySupplierId(supplierId)).thenReturn(List.of(existingCatalog));
        when(productVariantRepository.findAllById(List.of(10L))).thenReturn(List.of(variant));

        com.zone.agri.dto.request.supplier.SupplierProductCatalogRequest request = new com.zone.agri.dto.request.supplier.SupplierProductCatalogRequest();
        request.setProductVariantId(10L);
        request.setStatus(com.zone.agri.entity.enums.SupplierProductCatalogStatus.AVAILABLE);
        request.setVersion(1); // request version is 1 (mismatch)

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> supplierService.saveProductCatalog(supplierId, List.of(request)))
                .isInstanceOf(com.zone.agri.exception.ConflictException.class)
                .hasMessageContaining("đã được cập nhật bởi người dùng khác");
    }

    @Test
    void saveProductCatalog_shouldNotResetStatusChangedAtWhenOnlyNoteIsUpdated() {
        Long supplierId = 1L;
        Supplier supplier = Supplier.builder().id(supplierId).status(SupplierStatus.ACTIVE).build();
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();

        java.time.LocalDateTime originalTime = java.time.LocalDateTime.now().minusDays(5);
        SupplierProductCatalog existingCatalog = new SupplierProductCatalog();
        existingCatalog.setSupplier(supplier);
        existingCatalog.setProductVariant(variant);
        existingCatalog.setStatus(com.zone.agri.entity.enums.SupplierProductCatalogStatus.AVAILABLE);
        existingCatalog.setStatusChangedAt(originalTime);
        existingCatalog.setNote("Ghi chú cũ");
        existingCatalog.setVersion(1);

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(supplierProductCatalogRepository.findAllBySupplierId(supplierId)).thenReturn(List.of(existingCatalog));
        when(productVariantRepository.findAllById(List.of(10L))).thenReturn(List.of(variant));
        when(supplierProductCatalogRepository.findAllBySupplierId(supplierId)).thenReturn(List.of(existingCatalog));

        com.zone.agri.dto.request.supplier.SupplierProductCatalogRequest request = new com.zone.agri.dto.request.supplier.SupplierProductCatalogRequest();
        request.setProductVariantId(10L);
        request.setStatus(com.zone.agri.entity.enums.SupplierProductCatalogStatus.AVAILABLE); // Keep the same status
        request.setNote("Ghi chú mới");
        request.setVersion(1);

        supplierService.saveProductCatalog(supplierId, List.of(request));

        assertThat(existingCatalog.getNote()).isEqualTo("Ghi chú mới");
        assertThat(existingCatalog.getStatusChangedAt()).isEqualTo(originalTime); // statusChangedAt remains unchanged
    }

    @Test
    void testSupplierRequestPhoneValidation() {
        jakarta.validation.ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
        jakarta.validation.Validator validator = factory.getValidator();

        com.zone.agri.dto.request.supplier.SupplierRequest request = new com.zone.agri.dto.request.supplier.SupplierRequest();
        request.setName("CONG TY TNHH DE HEUS");
        request.setTaxCode("3701091716");
        request.setContactName("Johan Christiaan Van Den Ban");
        request.setPhone("02703 962736-2"); // landline format
        request.setEmail("deheus@gmail.com");
        request.setProvinceId("70");
        request.setAddressDetail("Lo A4, Khu Cong nghiep Hoa Phu");
        request.setStatus(SupplierStatus.ACTIVE);

        java.util.Set<jakarta.validation.ConstraintViolation<com.zone.agri.dto.request.supplier.SupplierRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}


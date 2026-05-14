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
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.ProductRepository;
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
                .thenThrow(new RuntimeException("lookup failed"));
        SupplierResponse response = supplierService.getSupplierById(7L);

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getCode()).isEqualTo("NCC-7");
        assertThat(response.getWarnings())
                .extracting(warning -> warning.getCode())
                .contains("ACTIVE_WITHOUT_CATALOG");
    }
}

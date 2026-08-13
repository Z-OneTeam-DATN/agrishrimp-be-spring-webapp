package com.zone.agri.service;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.response.product.ProductVariantResponse;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.AttributeValueRepository;
import com.zone.agri.repository.BrandRepository;
import com.zone.agri.repository.CategoryRepository;
import com.zone.agri.repository.InventoryNoteDetailRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.OrderItemRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.ProductImageRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.ProductVectorRepository;
import com.zone.agri.repository.SKUAttributeValueRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductServiceShippingWeightMappingTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AttributeValueRepository attributeValueRepository;

    @Mock
    private SKUAttributeValueRepository skuAttributeValueRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private InventoryNoteRepository inventoryNoteRepository;

    @Mock
    private InventoryNoteDetailRepository inventoryNoteDetailRepository;

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransferRepository inventoryTransferRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private SettingService settingService;

    @Mock
    private ImageSearchService imageSearchService;

    @Mock
    private ProductVectorRepository productVectorRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void mapVariantToResponse_exposesShippingWeight() {
        ProductVariant variant = ProductVariant.builder()
                .id(101L)
                .sku("SKU-101")
                .shippingWeight(new BigDecimal("1200"))
                .build();

        ProductVariantResponse response = productService.mapVariantToResponse(
                variant,
                null,
                BigDecimal.ONE,
                "NONE",
                List.of()
        );

        assertThat(response.getShippingWeight()).isEqualByComparingTo("1200");
    }

    @Test
    void normalizeShippingWeight_keepsPositiveWeightAndDropsInvalidValues() {
        BigDecimal valid = ReflectionTestUtils.invokeMethod(
                productService,
                "normalizeShippingWeight",
                new BigDecimal("500")
        );
        BigDecimal zero = ReflectionTestUtils.invokeMethod(
                productService,
                "normalizeShippingWeight",
                BigDecimal.ZERO
        );
        BigDecimal negative = ReflectionTestUtils.invokeMethod(
                productService,
                "normalizeShippingWeight",
                new BigDecimal("-1")
        );

        assertThat(valid).isEqualByComparingTo("500");
        assertThat(zero).isNull();
        assertThat(negative).isNull();
    }
}

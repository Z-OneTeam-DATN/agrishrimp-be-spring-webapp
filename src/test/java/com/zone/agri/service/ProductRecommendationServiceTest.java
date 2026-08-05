package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zone.agri.dto.response.product.ProductResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.OrderItem;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductRecommendation;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.entity.enums.VariantStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.CategoryRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.OrderItemRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.ProductRecommendationRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ca nhan hoa "Goi y danh cho ban" tren trang chu — tai dung nguyen bang product_recommendations
 * (market-basket that) da co san, chi khac o cho lay tap "san pham da mua" cua 1 user lam diem xuat
 * phat thay vi 1 san pham don le nhu "Khach hang thuong mua kem". Chay that voi DB H2 trong bo nho
 * (khong can Docker), khong mock gi — day la test dau tien seed du day Category/Product/
 * ProductVariant/Branch/Inventory/User/Order/OrderItem trong phien nay.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "security.jwt.secret-key=test-secret-key-for-jwt-util-in-test",
    "security.jwt.issuer=test-issuer",
    "security.jwt.expiry-time-in-seconds=86400",
    "security.jwt.refreshable-duration=86400",
    "mnl.tmp-dir=mnt/",
    "spring.datasource.url=jdbc:h2:mem:agri-product-recommendation-test;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.startup.schema-patches.enabled=false",
    "app.startup.seed-data.enabled=false",
    "spring.data.redis.repositories.enabled=false"
})
@Transactional
class ProductRecommendationServiceTest {

    @Autowired
    private ProductRecommendationService productRecommendationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRecommendationRepository productRecommendationRepository;

    private Category activeCategory;
    private Branch activeBranch;

    private Category activeCategory() {
        if (activeCategory == null) {
            activeCategory = categoryRepository.save(Category.builder()
                    .name("Danh muc test " + UUID.randomUUID())
                    .status(CategoryStatus.ACTIVE)
                    .build());
        }
        return activeCategory;
    }

    private Branch activeBranch() {
        if (activeBranch == null) {
            activeBranch = branchRepository.save(Branch.builder()
                    .name("Chi nhanh test " + UUID.randomUUID())
                    .status(BranchStatus.ACTIVE)
                    .build());
        }
        return activeBranch;
    }

    /** Tao 1 san pham ACTIVE + 1 variant, tuy chon co ton kho (in-stock) hay khong. */
    private Product seedProduct(String name, boolean inStock) {
        Product product = productRepository.save(Product.builder()
                .name(name)
                .slug("sp-" + UUID.randomUUID())
                .status(ProductStatus.ACTIVE)
                .category(activeCategory())
                .build());

        ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                .sku("SKU-" + UUID.randomUUID())
                .status(VariantStatus.ACTIVE)
                .product(product)
                .build());

        if (inStock) {
            inventoryRepository.save(Inventory.builder()
                    .quantity(20)
                    .branch(activeBranch())
                    .productVariant(variant)
                    .build());
        }
        return product;
    }

    private User seedCustomer() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .fullName("Khach test " + unique)
                .email(unique + "@agrishrimp.vn")
                .phoneNumber("0" + Math.abs(unique.hashCode() % 1_000_000_000))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build());
    }

    private void seedCompletedOrder(User user, Product product) {
        Order order = orderRepository.save(Order.builder()
                .code("ORD-" + UUID.randomUUID().toString().substring(0, 8))
                .status(OrderStatus.COMPLETED)
                .totalAmount(BigDecimal.TEN)
                .finalAmount(BigDecimal.TEN)
                .createdAt(LocalDateTime.now())
                .user(user)
                .build());

        ProductVariant variant = productVariantRepository.findAll().stream()
                .filter(v -> v.getProduct() != null && v.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElseThrow();

        orderItemRepository.save(OrderItem.builder()
                .order(order)
                .productVariant(variant)
                .quantity(1)
                .price(BigDecimal.TEN)
                .build());
    }

    private void seedRecommendation(Long productId, Long recommendedProductId, double lift) {
        productRecommendationRepository.save(ProductRecommendation.builder()
                .productId(productId)
                .recommendedProductId(recommendedProductId)
                .supportCount(5)
                .customerCount(5)
                .support(BigDecimal.valueOf(0.5))
                .confidence(BigDecimal.valueOf(0.6))
                .lift(BigDecimal.valueOf(lift))
                .calculatedAt(LocalDateTime.now())
                .build());
    }

    @Test
    void getPersonalizedRecommendations_userWithPurchaseHistory_returnsAssociatedProducts() {
        Product purchased = seedProduct("San pham da mua", false);
        Product recommended = seedProduct("San pham goi y", true);
        User user = seedCustomer();
        seedCompletedOrder(user, purchased);
        seedRecommendation(purchased.getId(), recommended.getId(), 2.0D);

        List<ProductResponse> result = productRecommendationService.getPersonalizedRecommendations(user.getId(), null);

        assertThat(result).extracting(ProductResponse::getId).containsExactly(recommended.getId());
    }

    @Test
    void getPersonalizedRecommendations_excludesAlreadyPurchasedProducts() {
        Product purchasedA = seedProduct("Da mua A", false);
        Product purchasedB = seedProduct("Da mua B", false);
        User user = seedCustomer();
        seedCompletedOrder(user, purchasedA);
        seedCompletedOrder(user, purchasedB);
        // B duoc goi y tu A, nhung B cung da duoc mua roi -> khong duoc xuat hien lai
        seedRecommendation(purchasedA.getId(), purchasedB.getId(), 3.0D);

        List<ProductResponse> result = productRecommendationService.getPersonalizedRecommendations(user.getId(), null);

        assertThat(result).isEmpty();
    }

    @Test
    void getPersonalizedRecommendations_userWithoutPurchaseHistory_returnsEmpty() {
        User user = seedCustomer();

        List<ProductResponse> result = productRecommendationService.getPersonalizedRecommendations(user.getId(), null);

        assertThat(result).isEmpty();
    }

    @Test
    void getPersonalizedRecommendations_guestUserIdNull_returnsEmpty() {
        List<ProductResponse> result = productRecommendationService.getPersonalizedRecommendations(null, null);

        assertThat(result).isEmpty();
    }
}

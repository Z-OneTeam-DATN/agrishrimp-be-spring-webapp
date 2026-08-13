package com.zone.agri.config;

import com.zone.agri.entity.Banner;
import com.zone.agri.entity.BlogCategory;
import com.zone.agri.entity.BlogPost;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Brand;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.Customer;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryReceiptPayment;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.OrderItem;
import com.zone.agri.entity.Permission;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductImage;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.User;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.BlogCategoryStatus;
import com.zone.agri.entity.enums.BlogPostStatus;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.BrandStatus;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.entity.enums.CustomerGender;
import com.zone.agri.entity.enums.CustomerStatus;
import com.zone.agri.entity.enums.Gender;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.entity.enums.PermissionGroup;
import com.zone.agri.entity.enums.PermissionType;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.entity.enums.SupplierPaymentMethod;
import com.zone.agri.entity.enums.SupplierStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.entity.enums.VariantStatus;
import com.zone.agri.entity.enums.VoucherDiscountType;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.repository.BannerRepository;
import com.zone.agri.repository.BlogCategoryRepository;
import com.zone.agri.repository.BlogPostRepository;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.BrandRepository;
import com.zone.agri.repository.CategoryRepository;
import com.zone.agri.repository.CustomerRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryReceiptPaymentRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.OrderItemRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.PermissionRepository;
import com.zone.agri.repository.ProductImageRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.repository.VoucherRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.startup.seed-data.enabled", havingValue = "true", matchIfMissing = true)
@org.springframework.core.annotation.Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final String CUSTOMER_ROLE_SLUG = "CUSTOMER";
    private static final String LEGACY_USER_ROLE_SLUG = "USER";
    private static final String ACTIVITY_LOG_MODULE_CODE = "ACTIVITY_LOG";
    private static final String ACTIVITY_LOG_VIEW_PERMISSION_CODE = "ACTIVITY_LOG_VIEW";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryReceiptPaymentRepository inventoryReceiptPaymentRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final VoucherRepository voucherRepository;
    private final BannerRepository bannerRepository;
    private final BlogCategoryRepository blogCategoryRepository;
    private final BlogPostRepository blogPostRepository;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        boolean hasExistingRoles = roleRepository.count() > 0;

        log.info(">>> ĐỒNG BỘ ROLE HỆ THỐNG VÀ MAPPING PERMISSION HIỆN CÓ...");
        Map<String, Role> seededRoles = seedSystemRolesAndBootstrapSuperAdmin();

        if (hasExistingRoles) {
            log.info(">>> ĐỒNG BỘ DỮ LIỆU NỀN TẢNG HOÀN TẤT.");
        } else {
            log.info(">>> KHỞI TẠO DỮ LIỆU NỀN TẢNG HOÀN TẤT.");
        }

        seedMasterDataAndOperations(seededRoles);
    }

    private void seedMasterDataAndOperations(Map<String, Role> seededRoles) {

        Branch cmBranch = ensureBranch("CN-CM01", "Chi nhánh Cà Mau", "STORE", "02903838388", "camau@agrishrimp.vn", "123 Trần Hưng Đạo, Phường 5, TP. Cà Mau", "Tỉnh Cà Mau", 87);
        Branch stBranch = ensureBranch("CN-ST01", "Chi nhánh Sóc Trăng", "STORE", "02993828288", "soctrang@agrishrimp.vn", "45 Lê Hồng Phong, Phường 3, TP. Sóc Trăng", "Tỉnh Sóc Trăng", 94);
        Branch btBranch = ensureBranch("CN-BT01", "Chi nhánh Bến Tre", "STORE", "02753818188", "bentre@agrishrimp.vn", "88 Nguyễn Đình Chiểu, Phường 2, TP. Bến Tre", "Tỉnh Bến Tre", 83);
        Branch blBranch = ensureBranch("CN-BL01", "Chi nhánh Bạc Liêu", "STORE", "02913828288", "baclieu@agrishrimp.vn", "26 Hai Bà Trưng, Phường 3, TP. Bạc Liêu", "Tỉnh Bạc Liêu", 95);
        List<Branch> branches = List.of(cmBranch, stBranch, btBranch, blBranch);

        seedDefaultSystemUsers(seededRoles, cmBranch);
        User admin = userRepository.findByEmail("admin@agrishrimp.vn").orElse(null);

        if (productRepository.count() == 0 || orderRepository.count() == 0) {
            log.info(">>> ĐANG KHỞI TẠO DANH MỤC SẢN PHẨM, TỒN KHO VÀ LỊCH SỬ GIAO DỊCH...");
            seedCatalogAndFinancialData(branches, admin);
        }

        seedVouchersIfEmpty();

        seedBannersIfEmpty();

        seedBlogIfEmpty(admin);
    }

    private void seedDefaultSystemUsers(Map<String, Role> seededRoles, Branch defaultBranch) {

        userRepository.findByEmail("staff@agrishrimp.vn").orElseGet(() -> userRepository.save(User.builder()
                .email("staff@agrishrimp.vn")
                .fullName("Nguyễn Thị Mai (Nhân viên Tư vấn)")
                .phoneNumber("0909000002")
                .passwordHash(passwordEncoder.encode("123456zoneteam"))
                .status(UserStatus.ACTIVE)
                .role(seededRoles.get("STAFF"))
                .branch(defaultBranch)
                .gender(Gender.FEMALE)
                .provider(AuthProvider.LOCAL)
                .addressDetail("123 Trần Hưng Đạo, P.5, TP. Cà Mau")
                .build()));

        userRepository.findByEmail("warehouse@agrishrimp.vn").orElseGet(() -> userRepository.save(User.builder()
                .email("warehouse@agrishrimp.vn")
                .fullName("Trần Văn Kho (Quản lý kho)")
                .phoneNumber("0909000003")
                .passwordHash(passwordEncoder.encode("123456zoneteam"))
                .status(UserStatus.ACTIVE)
                .role(seededRoles.get("WAREHOUSE_MANAGER"))
                .branch(defaultBranch)
                .gender(Gender.MALE)
                .provider(AuthProvider.LOCAL)
                .addressDetail("123 Trần Hưng Đạo, P.5, TP. Cà Mau")
                .build()));

        userRepository.findByEmail("agronomist@agrishrimp.vn").orElseGet(() -> userRepository.save(User.builder()
                .email("agronomist@agrishrimp.vn")
                .fullName("Kỹ Sư Lê Hoàng Thủy Sản")
                .phoneNumber("0909000004")
                .passwordHash(passwordEncoder.encode("123456zoneteam"))
                .status(UserStatus.ACTIVE)
                .role(seededRoles.get("AGRONOMIST"))
                .branch(defaultBranch)
                .gender(Gender.MALE)
                .provider(AuthProvider.LOCAL)
                .addressDetail("TP. Cà Mau")
                .build()));

        userRepository.findByEmail("customer@gmail.com").orElseGet(() -> {
            Role customerRole = seededRoles.get(CUSTOMER_ROLE_SLUG);
            User custUser = userRepository.save(User.builder()
                    .email("customer@gmail.com")
                    .fullName("Phạm Văn Khách (Demo)")
                    .phoneNumber("0909000005")
                    .passwordHash(passwordEncoder.encode("123456zoneteam"))
                    .status(UserStatus.ACTIVE)
                    .role(customerRole)
                    .branch(defaultBranch)
                    .gender(Gender.MALE)
                    .provider(AuthProvider.LOCAL)
                    .addressDetail("Đầm tôm Năm Căn, Cà Mau")
                    .build());

            customerRepository.save(Customer.builder()
                    .name("Phạm Văn Khách (Demo)")
                    .phone("0909000005")
                    .email("customer@gmail.com")
                    .gender(CustomerGender.MALE)
                    .status(CustomerStatus.ACTIVE)
                    .addressDetail("Đầm tôm Năm Căn, Cà Mau")
                    .assignedBranch(defaultBranch)
                    .user(custUser)
                    .build());
            return custUser;
        });
    }

    private void seedCatalogAndFinancialData(List<Branch> branches, User admin) {

        Category catFeed = ensureCategory("Thức ăn thủy sản", "thuc-an-thuy-san", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80");
        Category catProbiotic = ensureCategory("Men vi sinh & Chế phẩm sinh học", "men-vi-sinh-che-pham-sinh-hoc", "https://images.unsplash.com/photo-1576086213369-97a306d36557?auto=format&fit=crop&w=600&q=80");
        Category catMineral = ensureCategory("Khoáng chất & Dinh dưỡng", "khoang-chat-dinh-duong", "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=600&q=80");
        Category catChemical = ensureCategory("Thuốc & Xử lý môi trường nước", "thuoc-xu-ly-moi-truong-nuoc", "https://images.unsplash.com/photo-1559884743-74a57598c6c7?auto=format&fit=crop&w=600&q=80");
        Category catEquipment = ensureCategory("Thiết bị & Vật tư đầm tôm", "thiet-bi-vat-tu-dam-tom", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80");

        Brand brandCP = ensureBrand("Tập đoàn C.P. Việt Nam", "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?auto=format&fit=crop&w=200&q=80");
        Brand brandGrobest = ensureBrand("Grobest Việt Nam", "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?auto=format&fit=crop&w=200&q=80");
        Brand brandTrucAnh = ensureBrand("Trúc Anh Biotech", "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?auto=format&fit=crop&w=200&q=80");
        Brand brandBioMar = ensureBrand("BioMar Việt Nam", "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?auto=format&fit=crop&w=200&q=80");
        Brand brandShengLong = ensureBrand("Sheng Long Biotech", "https://images.unsplash.com/photo-1599305445671-ac291c95aaa9?auto=format&fit=crop&w=200&q=80");

        Supplier supCP = ensureSupplier("NCC-CP", "Công ty TNHH Thức Ăn Thủy Sản C.P. Việt Nam", "0300801234", "Nguyễn Văn Tâm", "0908111222", "sales@cp.com.vn", "KCN Sông Đốc, Cà Mau");
        Supplier supGrobest = ensureSupplier("NCC-GROBEST", "Công ty Cổ phần Grobest Việt Nam", "0301987654", "Lê Thanh Bình", "0908222333", "support@grobest.vn", "KCN An Nghiệp, Sóc Trăng");
        Supplier supTrucAnh = ensureSupplier("NCC-TRUCANH", "Công ty TNHH Sản Xuất & Thương Mại Trúc Anh Biotech", "1900654321", "Đỗ Thị Mai", "0908333444", "trucanh@biotech.vn", "Hiệp Thành, Bạc Liêu");
        Supplier supBioMar = ensureSupplier("NCC-BIOMAR", "Công ty TNHH BioMar Việt Nam", "0302345678", "Trần Quốc Bảo", "0908444555", "info@biomar.vn", "KCN Giao Long, Bến Tre");
        Supplier supShengLong = ensureSupplier("NCC-SHENGLONG", "Công ty TNHH Khoa Kỹ Sinh Học Thăng Long", "0303456789", "Hoàng Văn Long", "0908555666", "shenglong@shenglong.vn", "KCN Đức Hòa, Long An");
        List<Supplier> suppliers = List.of(supCP, supGrobest, supTrucAnh, supBioMar, supShengLong);

        Product pGrobest = ensureProduct("Thức ăn tôm thẻ Grobest Super Premium 1.5mm", "gb-feed-15mm", "Thức ăn tăng trưởng cao cấp cho tôm thẻ chân trắng bao 25kg", catFeed, brandGrobest, supGrobest, "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80");
        ProductVariant pvGrobest = ensureVariant(pGrobest, "GB-FEED-15MM", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80");

        Product pCP = ensureProduct("Thức ăn tôm thẻ C.P. 9920 - Bao 25kg", "cp-feed-9920", "Thức ăn đạm cao 40% cho tôm giai đoạn 30-60 ngày", catFeed, brandCP, supCP, "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80");
        ProductVariant pvCP = ensureVariant(pCP, "CP-FEED-9920", "https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=600&q=80");

        Product pMicro = ensureProduct("Men vi sinh xử lý đáy & nước Trúc Anh Micro-Pro 500g", "ta-micro-500g", "Chế phẩm vi sinh xử lý khí độc NO2/NH3 trong ao tôm", catProbiotic, brandTrucAnh, supTrucAnh, "https://images.unsplash.com/photo-1576086213369-97a306d36557?auto=format&fit=crop&w=600&q=80");
        ProductVariant pvMicro = ensureVariant(pMicro, "TA-MICRO-500G", "https://images.unsplash.com/photo-1576086213369-97a306d36557?auto=format&fit=crop&w=600&q=80");

        Product pStomi = ensureProduct("Khoáng tạt tôm thâm canh Stomi K-Mag 5kg", "min-stomi-5kg", "Tăng cường khoáng đa vi lượng, chống cong thân đục cơ", catMineral, brandBioMar, supBioMar, "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=600&q=80");
        ProductVariant pvStomi = ensureVariant(pStomi, "MIN-STOMI-5KG", "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=600&q=80");

        Product pBioBac = ensureProduct("Chế phẩm sinh học xử lý đáy ao BioBac 1kg", "bio-bac-1kg", "Phân hủy mùn bã hữu cơ và bùn đáy ao tôm", catProbiotic, brandShengLong, supShengLong, "https://images.unsplash.com/photo-1576086213369-97a306d36557?auto=format&fit=crop&w=600&q=80");
        ProductVariant pvBioBac = ensureVariant(pBioBac, "BIO-BAC-1KG", "https://images.unsplash.com/photo-1576086213369-97a306d36557?auto=format&fit=crop&w=600&q=80");

        Product pBKC = ensureProduct("Dung dịch diệt khuẩn BKC 80% Super 1 Lít", "chem-bkc-1l", "Diệt khuẩn, nấm, protozoa trong nước ao nuôi tôm", catChemical, brandTrucAnh, supTrucAnh, "https://images.unsplash.com/photo-1559884743-74a57598c6c7?auto=format&fit=crop&w=600&q=80");
        ProductVariant pvBKC = ensureVariant(pBKC, "CHEM-BKC-1L", "https://images.unsplash.com/photo-1559884743-74a57598c6c7?auto=format&fit=crop&w=600&q=80");

        Product pFan = ensureProduct("Quạt nước 4 cánh nuôi tôm công suất 2HP", "eq-fan-4b", "Bộ quạt tạo oxy đáy và dòng chảy cho ao tôm thâm canh", catEquipment, brandBioMar, supBioMar, "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80");
        ProductVariant pvFan = ensureVariant(pFan, "EQ-FAN-4B", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80");

        Product pFeeder = ensureProduct("Máy cho tôm ăn tự động dung tích 50kg", "eq-feeder-50kg", "Máy phun thức ăn tự động định giờ cho ao tôm", catEquipment, brandBioMar, supBioMar, "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80");
        ProductVariant pvFeeder = ensureVariant(pFeeder, "EQ-FEEDER-50KG", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80");

        List<ProductVariant> variants = List.of(pvGrobest, pvCP, pvMicro, pvStomi, pvBioBac, pvBKC, pvFan, pvFeeder);
        Map<ProductVariant, BigDecimal> importPrices = Map.of(
                pvGrobest, new BigDecimal("850000"),
                pvCP, new BigDecimal("800000"),
                pvMicro, new BigDecimal("130000"),
                pvStomi, new BigDecimal("190000"),
                pvBioBac, new BigDecimal("160000"),
                pvBKC, new BigDecimal("90000"),
                pvFan, new BigDecimal("2300000"),
                pvFeeder, new BigDecimal("3600000")
        );
        Map<ProductVariant, BigDecimal> sellPrices = Map.of(
                pvGrobest, new BigDecimal("1100000"),
                pvCP, new BigDecimal("1020000"),
                pvMicro, new BigDecimal("195000"),
                pvStomi, new BigDecimal("280000"),
                pvBioBac, new BigDecimal("240000"),
                pvBKC, new BigDecimal("145000"),
                pvFan, new BigDecimal("3400000"),
                pvFeeder, new BigDecimal("5100000")
        );

        Map<String, Inventory> inventoryMap = new HashMap<>();
        for (Branch b : branches) {
            for (ProductVariant v : variants) {
                Inventory inv = inventoryRepository.rawFindByBranchIdAndProductVariantId(b.getId(), v.getId()).stream().findFirst()
                        .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                                .branch(b)
                                .productVariant(v)
                                .quantity(800)
                                .defectiveQuantity(0)
                                .reservedQuantity(0)
                                .batchNumber("BATCH-2026-Q1")
                                .importPrice(importPrices.get(v))
                                .expiryDate(LocalDateTime.now().plusYears(2))
                                .shelfLocation("KHO-A" + b.getId())
                                .lastReceiptDate(LocalDateTime.now().minusMonths(3))
                                .minStock(10)
                                .build()));
                inventoryMap.put(b.getId() + "_" + v.getId(), inv);
            }
        }

        List<User> customerUsers = List.of(
                ensureCustomerUser("Nguyễn Văn Hùng", "hung.damdoi@gmail.com", "0918111001", "Trại tôm Đầm Dỗi, Phường 5, TP. Cà Mau", branches.get(0)),
                ensureCustomerUser("Trần Thị Mỹ Linh", "mylinh.myxuyen@gmail.com", "0918111002", "HTX Thủy sản Mỹ Xuyên, TP. Sóc Trăng", branches.get(1)),
                ensureCustomerUser("Lê Hoàng Nam", "hoangnam.bentre@gmail.com", "0918111003", "Nông hộ nuôi tôm Bến Tre, TP. Bến Tre", branches.get(2)),
                ensureCustomerUser("Phạm Quốc Việt", "viet.batri@gmail.com", "0918111004", "Trại tôm giống Ba Tri, Bến Tre", branches.get(2)),
                ensureCustomerUser("Võ Minh Trí", "tri.triphat@gmail.com", "0918111005", "Đại lý vật tư Trí Phát, Sóc Trăng", branches.get(1)),
                ensureCustomerUser("Đặng Văn Thành", "thanh.duyenhai@gmail.com", "0918111006", "Trại tôm thâm canh Duyên Hải, Cà Mau", branches.get(0))
        );

        LocalDateTime now = LocalDateTime.now();
        int noteSeq = 100;
        int paySeq = 100;
        int orderSeq = 1000;

        for (int m = 8; m >= 0; m--) {
            LocalDateTime monthBase = now.minusMonths(m);
            int daysInMonth = 28;

            for (int i = 0; i < 3; i++) {
                Supplier sup = suppliers.get((m + i) % suppliers.size());
                Branch branch = branches.get((m + i) % branches.size());
                LocalDateTime importDate = monthBase.withDayOfMonth(3 + i * 7).withHour(9).withMinute(30);

                noteSeq++;
                String noteCode = "NK-" + importDate.getYear() + String.format("%02d", importDate.getMonthValue()) + "-" + noteSeq;
                BigDecimal totalAmt = new BigDecimal((60 + (i * 40) + (m * 8)) * 1_000_000L);

                BigDecimal paidAmt;
                if (i == 0) {
                    paidAmt = totalAmt;
                } else if (i == 1) {
                    paidAmt = totalAmt.multiply(new BigDecimal("0.65")).setScale(2, RoundingMode.HALF_UP);
                } else {
                    paidAmt = (m == 0) ? BigDecimal.ZERO : totalAmt;
                }
                BigDecimal debtAmt = totalAmt.subtract(paidAmt);

                InventoryNote note = inventoryNoteRepository.save(InventoryNote.builder()
                        .code(noteCode)
                        .type(InventoryNoteType.IMPORT)
                        .status(InventoryNoteStatus.COMPLETED)
                        .supplier(sup)
                        .branch(branch)
                        .totalAmount(totalAmt)
                        .paymentAmount(paidAmt)
                        .debtAmount(debtAmt)
                        .createdBy(admin)
                        .createdAt(importDate)
                        .entryDate(importDate)
                        .reason("Nhập kho thức ăn & vật tư thủy sản tháng " + importDate.getMonthValue() + "/" + importDate.getYear())
                        .build());

                if (paidAmt.compareTo(BigDecimal.ZERO) > 0) {
                    paySeq++;
                    inventoryReceiptPaymentRepository.save(InventoryReceiptPayment.builder()
                            .inventoryNote(note)
                            .supplier(sup)
                            .branch(branch)
                            .createdBy(admin)
                            .paymentDate(importDate.plusDays(1))
                            .amount(paidAmt)
                            .remainingDebtAfter(debtAmt)
                            .paymentMethod(i % 2 == 0 ? SupplierPaymentMethod.TRANSFER : SupplierPaymentMethod.CASH)
                            .referenceCode("UNC-" + (880000 + paySeq))
                            .note("Thanh toán tiền hàng cho " + sup.getName() + " theo phiếu " + noteCode)
                            .createdAt(importDate.plusDays(1))
                            .build());
                }
            }

            int ordersThisMonth = 11 + (m % 4);
            for (int o = 0; o < ordersThisMonth; o++) {
                orderSeq++;
                int day = Math.min(1 + (o * 2), daysInMonth);
                LocalDateTime orderDate = monthBase.withDayOfMonth(day).withHour(8 + (o % 10)).withMinute(15 + (o * 4) % 40);

                User cust = customerUsers.get(o % customerUsers.size());
                Branch branch = branches.get(o % branches.size());

                ProductVariant v1 = variants.get(o % variants.size());
                ProductVariant v2 = variants.get((o + 3) % variants.size());
                int q1 = 2 + (o % 4);
                int q2 = 1 + (o % 3);

                BigDecimal p1Amt = sellPrices.get(v1).multiply(BigDecimal.valueOf(q1));
                BigDecimal p2Amt = sellPrices.get(v2).multiply(BigDecimal.valueOf(q2));
                BigDecimal totalProductAmt = p1Amt.add(p2Amt);
                BigDecimal shippingFee = new BigDecimal("45000");
                BigDecimal discount = (o % 4 == 0) ? new BigDecimal("35000") : BigDecimal.ZERO;
                BigDecimal finalAmt = totalProductAmt.add(shippingFee).subtract(discount);

                OrderStatus status = OrderStatus.COMPLETED;
                LocalDateTime completedAt = orderDate.plusHours(6);
                LocalDateTime receivedAt = orderDate.plusHours(5);
                LocalDateTime returnedAt = null;

                if (o == 2 && m > 0) {
                    status = OrderStatus.RETURNED;
                    returnedAt = orderDate.plusDays(2);
                }

                PaymentMethod payMethod = (o % 3 == 0) ? PaymentMethod.COD : ((o % 3 == 1) ? PaymentMethod.TRANSFER : PaymentMethod.CASH);
                PaymentStatus payStatus = (status == OrderStatus.RETURNED) ? PaymentStatus.REFUNDED :
                        ((o % 5 == 0 && payMethod == PaymentMethod.COD) ? PaymentStatus.UNPAID : PaymentStatus.PAID);

                String orderCode = "ORD-" + orderDate.getYear() + String.format("%02d", orderDate.getMonthValue()) + String.format("%02d", orderDate.getDayOfMonth()) + "-" + String.format("%04d", orderSeq);

                Order order = orderRepository.save(Order.builder()
                        .code(orderCode)
                        .user(cust)
                        .branch(branch)
                        .totalAmount(totalProductAmt)
                        .totalShippingFee(shippingFee)
                        .discountAmount(discount)
                        .finalAmount(finalAmt)
                        .paymentMethod(payMethod)
                        .paymentStatus(payStatus)
                        .status(status)
                        .createdAt(orderDate)
                        .receivedAt(receivedAt)
                        .completedAt(completedAt)
                        .returnedAt(returnedAt)
                        .receiverName(cust.getFullName())
                        .receiverPhone(cust.getPhoneNumber())
                        .shippingAddress(cust.getAddressDetail())
                        .build());

                OrderItem item1 = OrderItem.builder().order(order).productVariant(v1).quantity(q1).price(sellPrices.get(v1)).build();
                OrderItem item2 = OrderItem.builder().order(order).productVariant(v2).quantity(q2).price(sellPrices.get(v2)).build();
                orderItemRepository.saveAll(List.of(item1, item2));

                Inventory inv1 = inventoryMap.get(branch.getId() + "_" + v1.getId());
                Inventory inv2 = inventoryMap.get(branch.getId() + "_" + v2.getId());

                if (inv1 != null) {
                    inventoryTransactionRepository.save(InventoryTransaction.builder()
                            .type(TransactionType.SALE)
                            .quantityChange(-q1)
                            .newBalance(inv1.getQuantity() - q1)
                            .referenceCode(orderCode)
                            .reason("Xuất kho bán hàng đơn " + orderCode)
                            .inventory(inv1)
                            .createdBy(admin)
                            .createdAt(completedAt)
                            .build());
                }

                if (inv2 != null) {
                    inventoryTransactionRepository.save(InventoryTransaction.builder()
                            .type(TransactionType.SALE)
                            .quantityChange(-q2)
                            .newBalance(inv2.getQuantity() - q2)
                            .referenceCode(orderCode)
                            .reason("Xuất kho bán hàng đơn " + orderCode)
                            .inventory(inv2)
                            .createdBy(admin)
                            .createdAt(completedAt)
                            .build());
                }
            }
        }

        log.info(">>> KHỞI TẠO DỮ LIỆU MẪU BÁO CÁO TÀI CHÍNH (8 THÁNG) HOÀN TẤT THÀNH CÔNG!");
    }

    private void seedBannersIfEmpty() {
        if (bannerRepository.count() > 0) return;
        log.info(">>> ĐANG SEED BANNER TRANG CHỦ...");
        bannerRepository.save(Banner.builder()
                .title("AgriShrimp - Đồng Hành Cùng Người Nuôi Tôm Việt")
                .imageUrl("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=1600&q=80")
                .mobileImageUrl("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80")
                .linkUrl("/san-pham")
                .displayOrder(1)
                .isActive(true)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().plusYears(1))
                .build());

        bannerRepository.save(Banner.builder()
                .title("Bác Sĩ AI - Chẩn Đoán Bệnh Tôm Tức Thì Bằng Hình Ảnh")
                .imageUrl("https://images.unsplash.com/photo-1559884743-74a57598c6c7?auto=format&fit=crop&w=1600&q=80")
                .mobileImageUrl("https://images.unsplash.com/photo-1559884743-74a57598c6c7?auto=format&fit=crop&w=800&q=80")
                .linkUrl("/chan-doan-benh-tom-bang-ai")
                .displayOrder(2)
                .isActive(true)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().plusYears(1))
                .build());

        bannerRepository.save(Banner.builder()
                .title("Thức Ăn Thủy Sản & Men Vi Sinh Cao Cấp - Giao Tận Đầm")
                .imageUrl("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1600&q=80")
                .mobileImageUrl("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80")
                .linkUrl("/san-pham")
                .displayOrder(3)
                .isActive(true)
                .startDate(LocalDateTime.now().minusMonths(1))
                .endDate(LocalDateTime.now().plusYears(1))
                .build());
    }

    private void seedVouchersIfEmpty() {
        if (voucherRepository.count() > 0) return;
        log.info(">>> ĐANG SEED VOUCHER KHUYẾN MÃI...");
        voucherRepository.save(Voucher.builder()
                .code("CHAOBANMOI")
                .title("Giảm 10% cho khách hàng mới")
                .discountType(VoucherDiscountType.PERCENT)
                .value(new BigDecimal("10"))
                .maxDiscount(new BigDecimal("50000"))
                .minOrderValue(new BigDecimal("200000"))
                .quantity(500)
                .maxUsagePerUser(1)
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().plusMonths(6))
                .status(VoucherStatus.ACTIVE)
                .build());

        voucherRepository.save(Voucher.builder()
                .code("AGRISHRIMP50K")
                .title("Giảm trực tiếp 50.000đ")
                .discountType(VoucherDiscountType.FIXED)
                .value(new BigDecimal("50000"))
                .maxDiscount(new BigDecimal("50000"))
                .minOrderValue(new BigDecimal("500000"))
                .quantity(200)
                .maxUsagePerUser(2)
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().plusMonths(6))
                .status(VoucherStatus.ACTIVE)
                .build());

        voucherRepository.save(Voucher.builder()
                .code("FREESHIP")
                .title("Miễn phí vận chuyển 30.000đ")
                .discountType(VoucherDiscountType.FIXED)
                .value(new BigDecimal("30000"))
                .maxDiscount(new BigDecimal("30000"))
                .minOrderValue(new BigDecimal("300000"))
                .quantity(1000)
                .maxUsagePerUser(5)
                .startDate(LocalDateTime.now().minusDays(30))
                .endDate(LocalDateTime.now().plusMonths(6))
                .status(VoucherStatus.ACTIVE)
                .build());
    }

    private void seedBlogIfEmpty(User author) {
        if (blogPostRepository.count() > 0) return;
        log.info(">>> ĐANG SEED BÀI VIẾT KỸ THUẬT VÀ BLOG...");
        BlogCategory catKyThuat = blogCategoryRepository.findBySlug("ky-thuat-nuoi-tom")
                .orElseGet(() -> blogCategoryRepository.save(BlogCategory.builder()
                        .name("Kỹ thuật nuôi tôm")
                        .slug("ky-thuat-nuoi-tom")
                        .description("Các bài viết chia sẻ kinh nghiệm và kỹ thuật nuôi tôm công nghệ cao")
                        .status(BlogCategoryStatus.ACTIVE)
                        .build()));

        BlogCategory catBenh = blogCategoryRepository.findBySlug("phong-ngua-dich-benh")
                .orElseGet(() -> blogCategoryRepository.save(BlogCategory.builder()
                        .name("Phòng ngừa dịch bệnh")
                        .slug("phong-ngua-dich-benh")
                        .description("Nhận diện, phòng ngừa và phác đồ điều trị các bệnh thường gặp trên tôm")
                        .status(BlogCategoryStatus.ACTIVE)
                        .build()));

        blogPostRepository.save(BlogPost.builder()
                .title("Hướng dẫn kỹ thuật quản lý màu nước ao nuôi tôm thẻ chân trắng hiệu quả")
                .slug("huong-dan-ky-thuat-quan-ly-mau-nuoc-ao-nuoi-tom")
                .excerpt("Màu nước phản ánh trực tiếp sức khỏe môi trường ao nuôi và hệ vi sinh. Bài viết hướng dẫn cách tạo và duy trì màu nước đọt chuối non ổn định suốt vụ nuôi.")
                .content("<p>Màu nước ao nuôi tôm lý tưởng nhất là màu trà hoặc xanh đọt chuối non do tảo khuê (diatom) và tảo lục phát triển ổn định. Định kỳ bổ sung men vi sinh và khoáng chất giúp giữ môi trường ao nuôi luôn trong lành.</p>")
                .thumbnailUrl("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80")
                .status(BlogPostStatus.PUBLISHED)
                .author(author)
                .category(catKyThuat)
                .viewCount(128L)
                .publishedAt(LocalDateTime.now().minusDays(15))
                .build());

        blogPostRepository.save(BlogPost.builder()
                .title("Nhận biết sớm và phác đồ xử lý bệnh phân trắng trên tôm thẻ")
                .slug("nhan-biet-som-va-phac-do-xu-ly-benh-phan-trang")
                .excerpt("Bệnh phân trắng là một trong những nỗi lo lớn nhất của bà con nuôi tôm. Cùng tìm hiểu nguyên nhân từ thức ăn, khuẩn Vibrio và phác đồ điều trị dứt điểm.")
                .content("<p>Bệnh phân trắng xuất phát từ nhiều nguyên nhân: nhiễm khuẩn Vibrio, ký sinh trùng Gregarine hoặc độc tố nấm mốc trong thức ăn. Khi phát hiện tôm có sợi phân trắng nổi trên mặt nước, cần giảm 30-50% lượng thức ăn, tăng cường quạt nước và xử lý men vi sinh đậm đặc.</p>")
                .thumbnailUrl("https://images.unsplash.com/photo-1559884743-74a57598c6c7?auto=format&fit=crop&w=800&q=80")
                .status(BlogPostStatus.PUBLISHED)
                .author(author)
                .category(catBenh)
                .viewCount(256L)
                .publishedAt(LocalDateTime.now().minusDays(7))
                .build());
    }

    private Branch ensureBranch(String code, String name, String type, String phone, String email, String address, String provinceName, Integer provinceId) {
        return branchRepository.findByBranchCode(code)
                .orElseGet(() -> branchRepository.save(Branch.builder()
                        .branchCode(code)
                        .branchType(type)
                        .name(name)
                        .phone(phone)
                        .email(email)
                        .addressDetail(address)
                        .fullAddress(address)
                        .provinceName(provinceName)
                        .provinceId(provinceId)
                        .status(BranchStatus.ACTIVE)
                        .build()));
    }

    private Category ensureCategory(String name, String slug, String imageUrl) {
        return categoryRepository.searchCategories(name, null).stream().findFirst()
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(name)
                        .imageUrl(imageUrl)
                        .status(CategoryStatus.ACTIVE)
                        .build()));
    }

    private Brand ensureBrand(String name, String logoUrl) {
        return brandRepository.findByName(name)
                .orElseGet(() -> brandRepository.save(Brand.builder()
                        .name(name)
                        .logoUrl(logoUrl)
                        .status(BrandStatus.ACTIVE)
                        .build()));
    }

    private Supplier ensureSupplier(String code, String name, String taxCode, String contact, String phone, String email, String address) {
        return supplierRepository.findByCode(code)
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .code(code)
                        .name(name)
                        .taxCode(taxCode)
                        .contactName(contact)
                        .phone(phone)
                        .email(email)
                        .addressDetail(address)
                        .status(SupplierStatus.ACTIVE)
                        .build()));
    }

    private Product ensureProduct(String name, String slug, String shortDesc, Category category, Brand brand, Supplier supplier, String imageUrl) {
        return productRepository.findBySlug(slug)
                .orElseGet(() -> {
                    Product p = productRepository.save(Product.builder()
                            .name(name)
                            .slug(slug)
                            .shortDesc(shortDesc)
                            .description("<p>" + shortDesc + ". Sản phẩm chính hãng chất lượng cao, an toàn cho đầm tôm và môi trường nước.</p>")
                            .category(category)
                            .brand(brand)
                            .suppliers(new HashSet<>(Set.of(supplier)))
                            .status(ProductStatus.ACTIVE)
                            .ratingAverage(4.8f)
                            .reviewCount(15)
                            .createdAt(LocalDateTime.now().minusMonths(9))
                            .build());

                    if (imageUrl != null && !imageUrl.isBlank()) {
                        String imgName = name.length() > 200 ? name.substring(0, 200) : name;
                        String imgText = imgName.length() > 90 ? imgName.substring(0, 90) : imgName;
                        productImageRepository.save(ProductImage.builder()
                                .product(p)
                                .imageUrl(imageUrl)
                                .name(imgName)
                                .text(imgText)
                                .build());
                    }
                    return p;
                });
    }

    private ProductVariant ensureVariant(Product product, String sku, String imageUrl) {
        return productVariantRepository.findBySku(sku)
                .orElseGet(() -> productVariantRepository.save(ProductVariant.builder()
                        .product(product)
                        .sku(sku)
                        .imageUrl(imageUrl)
                        .status(VariantStatus.ACTIVE)
                        .build()));
    }

    private User ensureCustomerUser(String fullName, String email, String phone, String address, Branch branch) {
        return userRepository.findByEmail(email)
                .or(() -> userRepository.findByPhoneNumber(phone))
                .orElseGet(() -> {
                    Role customerRole = roleRepository.findBySlug("CUSTOMER")
                            .orElseGet(() -> roleRepository.save(Role.builder()
                                    .slug("CUSTOMER")
                                    .displayName("Khách hàng")
                                    .isSystem(false)
                                    .isActive(true)
                                    .description("Khách hàng cá nhân / đầm tôm")
                                    .build()));

                    User u = User.builder()
                            .fullName(fullName)
                            .email(email)
                            .phoneNumber(phone)
                            .passwordHash(passwordEncoder.encode("123456zoneteam"))
                            .status(UserStatus.ACTIVE)
                            .role(customerRole)
                            .branch(branch)
                            .gender(Gender.MALE)
                            .provider(AuthProvider.LOCAL)
                            .addressDetail(address)
                            .build();

                    User savedUser = userRepository.save(u);

                    Customer customer = Customer.builder()
                            .name(fullName)
                            .phone(phone)
                            .email(email)
                            .gender(CustomerGender.MALE)
                            .status(CustomerStatus.ACTIVE)
                            .addressDetail(address)
                            .assignedBranch(branch)
                            .user(savedUser)
                            .build();

                    customerRepository.save(customer);
                    return savedUser;
                });
    }

    private Map<String, Role> seedSystemRolesAndBootstrapSuperAdmin() {
        Set<String> roleSlugsBeforeSeed = roleRepository.findAll().stream()
                .map(Role::getSlug)
                .filter(slug -> slug != null && !slug.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
        log.info("Roles trước khi seed: {} [{}]",
                roleSlugsBeforeSeed.size(),
                String.join(", ", roleSlugsBeforeSeed));

        seedSystemPermissionsIfMissing();
        List<Permission> allPermissions = permissionRepository.findAll();
        Map<String, Permission> permissionsByCode = allPermissions.stream()
                .filter(permission -> permission.getCode() != null && !permission.getCode().isBlank())
                .collect(Collectors.toMap(
                        permission -> permission.getCode().trim(),
                        permission -> permission,
                        (existing, duplicate) -> existing));

        log.info("Permission hiện có trong DB: {} [{}]",
                permissionsByCode.size(),
                permissionsByCode.keySet().stream().sorted().collect(Collectors.joining(", ")));

        List<RoleSeedSpec> roleSpecs = buildRoleSeedSpecs();
        Set<String> explicitlyMappedCodes = roleSpecs.stream()
                .map(RoleSeedSpec::permissionCodes)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(TreeSet::new));
        explicitlyMappedCodes.addAll(superAdminOnlyPermissionCodes());

        Map<String, Integer> mappedCounts = new HashMap<>();
        Map<String, Role> seededRoles = new HashMap<>();
        for (RoleSeedSpec spec : roleSpecs) {
            Set<Permission> permissions = resolveExistingPermissions(
                    spec.slug(),
                    spec.permissionCodes(),
                    permissionsByCode);
            Role role = upsertSystemRole(spec, permissions);
            seededRoles.put(role.getSlug(), role);
            mappedCounts.put(role.getSlug(), role.getPermissions() == null ? 0 : role.getPermissions().size());
        }

        RoleSeedSpec superAdminSpec = new RoleSeedSpec(
                "SUPER_ADMIN",
                "Siêu quản trị viên",
                "Quyền cao nhất, quản lý toàn bộ hệ thống",
                Set.of());
        Role superAdminRole = upsertSystemRole(superAdminSpec, new HashSet<>(allPermissions));
        seededRoles.put(superAdminRole.getSlug(), superAdminRole);
        mappedCounts.put(
                superAdminRole.getSlug(),
                superAdminRole.getPermissions() == null ? 0 : superAdminRole.getPermissions().size());

        Set<String> unmappedCodes = permissionsByCode.keySet().stream()
                .filter(code -> !explicitlyMappedCodes.contains(code))
                .collect(Collectors.toCollection(TreeSet::new));
        if (unmappedCodes.isEmpty()) {
            log.info("Unmapped permissions: 0");
        } else {
            log.warn("Unmapped permissions: {} [{}]", unmappedCodes.size(), String.join(", ", unmappedCodes));
        }

        log.info("| Role | Số Permission | Mô tả |");
        log.info("|---|---:|---|");
        roleSpecs.forEach(spec -> log.info(
                "| {} | {} | {} |",
                spec.slug(),
                mappedCounts.getOrDefault(spec.slug(), 0),
                spec.displayName()));
        log.info("| SUPER_ADMIN | {}/{} | Toàn hệ thống |",
                mappedCounts.getOrDefault("SUPER_ADMIN", 0),
                allPermissions.size());

        migrateLegacyUserRoleToCustomer(seededRoles.get(CUSTOMER_ROLE_SLUG));
        bootstrapSuperAdmin(superAdminRole);

        return seededRoles;
    }

    private List<RoleSeedSpec> buildRoleSeedSpecs() {
        return List.of(
                new RoleSeedSpec(
                        CUSTOMER_ROLE_SLUG,
                        "Khách hàng",
                        "Người dùng cuối của hệ thống",
                        Set.of()),
                new RoleSeedSpec(
                        "STAFF",
                        "Nhân viên bán hàng / Tư vấn",
                        "Tiếp nhận, tư vấn và xử lý đơn hàng trong phạm vi nghiệp vụ bán hàng",
                        codes(
                                "DASHBOARD", "DASHBOARD_VIEW",
                                "WORKSPACE", "WORKSPACE_VIEW",
                                "REPORT", "REPORT_INVENTORY_VIEW",
                                "ORDER", "ORDER_VIEW", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_CONFIRM",
                                "ORDER_SHIP", "ORDER_CANCEL", "ORDER_COMPLETE", "ORDER_EXPORT",
                                "CUSTOMER", "CUSTOMER_VIEW",
                                "VOUCHER", "VOUCHER_VIEW",
                                "PRODUCT", "PRODUCT_VIEW",
                                "CATEGORY", "CATEGORY_VIEW",
                                "ATTRIBUTE", "ATTRIBUTE_VIEW",
                                "CHAT", "CHAT_VIEW", "CHAT_MANAGE",
                                "CUSTOMER_ADVISOR", "CUSTOMER_ADVISOR_USE")),
                new RoleSeedSpec(
                        "WAREHOUSE_MANAGER",
                        "Quản lý kho / Thủ kho",
                        "Quản lý tồn kho, nhập xuất, điều chuyển, kiểm kê và yêu cầu mua hàng",
                        codes(
                                "DASHBOARD", "DASHBOARD_VIEW",
                                "WORKSPACE", "WORKSPACE_VIEW",
                                "REPORT", "REPORT_INVENTORY_VIEW",
                                "PRODUCT", "PRODUCT_VIEW",
                                "CATEGORY", "CATEGORY_VIEW",
                                "ATTRIBUTE", "ATTRIBUTE_VIEW",
                                "SUPPLIER", "SUPPLIER_VIEW", "SUPPLIER_CREATE", "SUPPLIER_UPDATE", "SUPPLIER_DELETE",
                                "DRIVER", "DRIVER_VIEW", "DRIVER_CREATE", "DRIVER_UPDATE", "DRIVER_DELETE",
                                "IMPORT", "IMPORT_VIEW", "IMPORT_CREATE", "IMPORT_UPDATE", "IMPORT_APPROVE",
                                "IMPORT_CANCEL", "IMPORT_DELETE",
                                "EXPORT", "EXPORT_VIEW", "EXPORT_CREATE", "EXPORT_UPDATE", "EXPORT_APPROVE",
                                "EXPORT_CANCEL", "EXPORT_DELETE",
                                "TRANSFER", "TRANSFER_VIEW", "TRANSFER_CREATE", "TRANSFER_UPDATE", "TRANSFER_APPROVE",
                                "TRANSFER_CANCEL", "TRANSFER_DELETE",
                                "INVENTORY_CHECK", "INVENTORY_CHECK_VIEW", "INVENTORY_CHECK_CREATE",
                                "INVENTORY_CHECK_UPDATE", "INVENTORY_CHECK_APPROVE", "INVENTORY_CHECK_CANCEL",
                                "INVENTORY_CHECK_DELETE",
                                "PURCHASE_REQUEST", "PURCHASE_REQUEST_VIEW", "PURCHASE_REQUEST_CREATE",
                                "PURCHASE_REQUEST_UPDATE", "PURCHASE_REQUEST_APPROVE", "PURCHASE_REQUEST_DELETE")),
                new RoleSeedSpec(
                        "AGRONOMIST",
                        "Kỹ sư nông nghiệp",
                        "Quản lý tri thức AI Doctor, xử lý ca bệnh và tư vấn kỹ thuật",
                        codes(
                                "DASHBOARD", "DASHBOARD_VIEW",
                                "WORKSPACE", "WORKSPACE_VIEW",
                                "PRODUCT", "PRODUCT_VIEW",
                                "CATEGORY", "CATEGORY_VIEW",
                                "CHAT", "CHAT_VIEW",
                                "CUSTOMER_ADVISOR", "CUSTOMER_ADVISOR_USE",
                                "AGRONOMIST_WORKSPACE", "AGRONOMIST_WORKSPACE_USE",
                                "AI_KNOWLEDGE", "AI_KNOWLEDGE_VIEW", "AI_KNOWLEDGE_CREATE",
                                "AI_KNOWLEDGE_UPDATE", "AI_KNOWLEDGE_APPROVE", "AI_IMPORT_KNOWLEDGE",
                                "AI_CASE_REVIEW")),
                new RoleSeedSpec(
                        "ADMIN",
                        "Quản trị viên",
                        "Quản lý hoạt động doanh nghiệp trong phạm vi được phân quyền",
                        codes(
                                "DASHBOARD", "DASHBOARD_VIEW",
                                "WORKSPACE", "WORKSPACE_VIEW",
                                "REPORT", "REPORT_REVENUE_VIEW", "REPORT_REVENUE_VIEW_ALL_BRANCHES",
                                "REPORT_INVENTORY_VIEW",
                                "REPORT_INVENTORY_VIEW_ALL_BRANCHES", "REPORT_FINANCE_VIEW",
                                "REPORT_FINANCE_VIEW_ALL_BRANCHES",
                                "STAFF", "STAFF_VIEW", "STAFF_CREATE", "STAFF_UPDATE", "STAFF_DELETE",
                                "BRANCH", "BRANCH_VIEW", "BRANCH_CREATE", "BRANCH_UPDATE", "BRANCH_DELETE",
                                "ORDER", "ORDER_VIEW", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_CONFIRM",
                                "ORDER_SHIP", "ORDER_CANCEL", "ORDER_COMPLETE", "ORDER_EXPORT", "ORDER_REFUND",
                                "ORDER_DELETE",
                                "CUSTOMER", "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_DELETE",
                                "VOUCHER", "VOUCHER_VIEW", "VOUCHER_CREATE", "VOUCHER_UPDATE", "VOUCHER_DELETE",
                                "PRODUCT", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE",
                                "CATEGORY", "CATEGORY_VIEW", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DELETE",
                                "ATTRIBUTE", "ATTRIBUTE_VIEW", "ATTRIBUTE_CREATE", "ATTRIBUTE_UPDATE", "ATTRIBUTE_DELETE",
                                "SUPPLIER", "SUPPLIER_VIEW", "SUPPLIER_CREATE", "SUPPLIER_UPDATE", "SUPPLIER_DELETE",
                                "DRIVER", "DRIVER_VIEW", "DRIVER_CREATE", "DRIVER_UPDATE", "DRIVER_DELETE",
                                "IMPORT", "IMPORT_VIEW",
                                "EXPORT", "EXPORT_VIEW",
                                "TRANSFER", "TRANSFER_VIEW",
                                "INVENTORY_CHECK", "INVENTORY_CHECK_VIEW",
                                "PURCHASE_REQUEST", "PURCHASE_REQUEST_VIEW",
                                ACTIVITY_LOG_MODULE_CODE, ACTIVITY_LOG_VIEW_PERMISSION_CODE,
                                "BANNER", "BANNER_VIEW", "BANNER_CREATE", "BANNER_EDIT", "BANNER_DELETE",
                                "BLOG", "BLOG_VIEW", "BLOG_CREATE", "BLOG_EDIT", "BLOG_DELETE", "BLOG_APPROVE",
                                "SETTING", "SETTING_VIEW", "SETTING_UPDATE",
                                "CHAT", "CHAT_VIEW", "CHAT_MANAGE")));
    }

    private void seedSystemPermissionsIfMissing() {
        Map<String, Permission> modulesByCode = new HashMap<>();
        int createdModules = 0;
        int createdActions = 0;

        for (PermissionModuleSeedSpec moduleSpec : buildPermissionModuleSpecs()) {
            Optional<Permission> existingOpt = permissionRepository.findByCode(moduleSpec.code());
            Permission module;
            if (existingOpt.isPresent()) {
                module = existingOpt.get();
                boolean changed = false;
                if (!moduleSpec.name().equals(module.getName())) {
                    module.setName(moduleSpec.name());
                    changed = true;
                }
                if (module.getType() != PermissionType.MODULE) {
                    module.setType(PermissionType.MODULE);
                    changed = true;
                }
                if (module.getGroupName() != moduleSpec.groupName()) {
                    module.setGroupName(moduleSpec.groupName());
                    changed = true;
                }
                if (module.getParentId() != null) {
                    module.setParentId(null);
                    changed = true;
                }
                if (changed) {
                    module = permissionRepository.save(module);
                }
            } else {
                module = permissionRepository.save(Permission.builder()
                        .name(moduleSpec.name())
                        .code(moduleSpec.code())
                        .type(PermissionType.MODULE)
                        .groupName(moduleSpec.groupName())
                        .parentId(null)
                        .build());
                createdModules++;
            }
            modulesByCode.put(module.getCode(), module);
        }

        for (PermissionActionSeedSpec actionSpec : buildPermissionActionSpecs()) {
            Permission parentModule = modulesByCode.get(actionSpec.parentCode());
            if (parentModule == null) {
                log.warn("Bỏ qua seed action {} do không tìm thấy module cha {}",
                        actionSpec.code(),
                        actionSpec.parentCode());
                continue;
            }

            Optional<Permission> existingOpt = permissionRepository.findByCode(actionSpec.code());
            if (existingOpt.isPresent()) {
                Permission action = existingOpt.get();
                boolean changed = false;
                if (!actionSpec.name().equals(action.getName())) {
                    action.setName(actionSpec.name());
                    changed = true;
                }
                if (action.getType() != PermissionType.ACTION) {
                    action.setType(PermissionType.ACTION);
                    changed = true;
                }
                if (action.getGroupName() != actionSpec.groupName()) {
                    action.setGroupName(actionSpec.groupName());
                    changed = true;
                }
                Long existingParentId = action.getParentId();
                if (existingParentId == null || !existingParentId.equals(parentModule.getId())) {
                    action.setParentId(parentModule.getId());
                    changed = true;
                }
                if (changed) {
                    permissionRepository.save(action);
                }
            } else {
                permissionRepository.save(Permission.builder()
                        .name(actionSpec.name())
                        .code(actionSpec.code())
                        .type(PermissionType.ACTION)
                        .groupName(actionSpec.groupName())
                        .parentId(parentModule.getId())
                        .build());
                createdActions++;
            }
        }

        if (createdModules > 0 || createdActions > 0) {
            log.info("Đã seed thêm {} module permission và {} action permission còn thiếu.",
                    createdModules,
                    createdActions);
        }
    }

    private List<PermissionModuleSeedSpec> buildPermissionModuleSpecs() {
        return List.of(
                new PermissionModuleSeedSpec("Tổng quan", "DASHBOARD", PermissionGroup.SYSTEM),
                new PermissionModuleSeedSpec("Không gian làm việc", "WORKSPACE", PermissionGroup.SYSTEM),
                new PermissionModuleSeedSpec("Quản lý Đơn hàng", "ORDER", PermissionGroup.SALES),
                new PermissionModuleSeedSpec("Quản lý Sản phẩm", "PRODUCT", PermissionGroup.PRODUCT_CATALOG),
                new PermissionModuleSeedSpec("Quản lý Danh mục", "CATEGORY", PermissionGroup.PRODUCT_CATALOG),
                new PermissionModuleSeedSpec("Quản lý Thuộc tính", "ATTRIBUTE", PermissionGroup.PRODUCT_CATALOG),
                new PermissionModuleSeedSpec("Quản lý Khách hàng", "CUSTOMER", PermissionGroup.SALES),
                new PermissionModuleSeedSpec("Quản lý Voucher", "VOUCHER", PermissionGroup.SALES),
                new PermissionModuleSeedSpec("Quản lý Banner", "BANNER", PermissionGroup.SALES),
                new PermissionModuleSeedSpec("Quản lý Bài viết", "BLOG", PermissionGroup.SALES),
                new PermissionModuleSeedSpec("Quản lý Chi nhánh", "BRANCH", PermissionGroup.SYSTEM),
                new PermissionModuleSeedSpec("Quản lý Nhà cung cấp", "SUPPLIER", PermissionGroup.INVENTORY),
                new PermissionModuleSeedSpec("Quản lý Tài xế", "DRIVER", PermissionGroup.INVENTORY),
                new PermissionModuleSeedSpec("Quản lý Nhập kho", "IMPORT", PermissionGroup.INVENTORY),
                new PermissionModuleSeedSpec("Quản lý Xuất kho", "EXPORT", PermissionGroup.INVENTORY),
                new PermissionModuleSeedSpec("Quản lý Điều chuyển kho", "TRANSFER", PermissionGroup.INVENTORY),
                new PermissionModuleSeedSpec("Quản lý Kiểm kê kho", "INVENTORY_CHECK", PermissionGroup.INVENTORY),
                new PermissionModuleSeedSpec("Yêu cầu nhập hàng", "PURCHASE_REQUEST", PermissionGroup.INVENTORY),
                new PermissionModuleSeedSpec("Báo cáo & Thống kê", "REPORT", PermissionGroup.REPORT),
                new PermissionModuleSeedSpec("Quản lý Nhân viên", "STAFF", PermissionGroup.SYSTEM),
                new PermissionModuleSeedSpec("Quản lý Vai trò", "ROLE", PermissionGroup.SYSTEM),
                new PermissionModuleSeedSpec("Cài đặt hệ thống", "SETTING", PermissionGroup.SYSTEM),
                new PermissionModuleSeedSpec("Tin nhắn & Chat", "CHAT", PermissionGroup.SYSTEM),
                new PermissionModuleSeedSpec("Nhật ký hoạt động", ACTIVITY_LOG_MODULE_CODE, PermissionGroup.SYSTEM),
                new PermissionModuleSeedSpec("AI Doctor Knowledge", "AI_KNOWLEDGE", PermissionGroup.AI_KNOWLEDGE),
                new PermissionModuleSeedSpec("Trợ lý tư vấn khách hàng", "CUSTOMER_ADVISOR", PermissionGroup.AI_KNOWLEDGE),
                new PermissionModuleSeedSpec("Không gian kỹ sư", "AGRONOMIST_WORKSPACE", PermissionGroup.AI_KNOWLEDGE));
    }

    private List<PermissionActionSeedSpec> buildPermissionActionSpecs() {
        return List.of(
                new PermissionActionSeedSpec("Xem Tổng quan", "DASHBOARD_VIEW", PermissionGroup.SYSTEM, "DASHBOARD"),
                new PermissionActionSeedSpec("Xem Không gian làm việc", "WORKSPACE_VIEW", PermissionGroup.SYSTEM, "WORKSPACE"),
                new PermissionActionSeedSpec("Xem Đơn hàng", "ORDER_VIEW", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Tạo Đơn hàng", "ORDER_CREATE", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Sửa Đơn hàng", "ORDER_UPDATE", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Xác nhận Đơn hàng", "ORDER_CONFIRM", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Giao Đơn hàng", "ORDER_SHIP", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Hủy Đơn hàng", "ORDER_CANCEL", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Hoàn thành Đơn hàng", "ORDER_COMPLETE", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Xuất danh sách Đơn hàng", "ORDER_EXPORT", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Hoàn tiền Đơn hàng", "ORDER_REFUND", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Xóa Đơn hàng", "ORDER_DELETE", PermissionGroup.SALES, "ORDER"),
                new PermissionActionSeedSpec("Xem Sản phẩm", "PRODUCT_VIEW", PermissionGroup.PRODUCT_CATALOG, "PRODUCT"),
                new PermissionActionSeedSpec("Tạo Sản phẩm", "PRODUCT_CREATE", PermissionGroup.PRODUCT_CATALOG, "PRODUCT"),
                new PermissionActionSeedSpec("Sửa Sản phẩm", "PRODUCT_UPDATE", PermissionGroup.PRODUCT_CATALOG, "PRODUCT"),
                new PermissionActionSeedSpec("Xóa Sản phẩm", "PRODUCT_DELETE", PermissionGroup.PRODUCT_CATALOG, "PRODUCT"),
                new PermissionActionSeedSpec("Xem Danh mục", "CATEGORY_VIEW", PermissionGroup.PRODUCT_CATALOG, "CATEGORY"),
                new PermissionActionSeedSpec("Tạo Danh mục", "CATEGORY_CREATE", PermissionGroup.PRODUCT_CATALOG, "CATEGORY"),
                new PermissionActionSeedSpec("Sửa Danh mục", "CATEGORY_UPDATE", PermissionGroup.PRODUCT_CATALOG, "CATEGORY"),
                new PermissionActionSeedSpec("Xóa Danh mục", "CATEGORY_DELETE", PermissionGroup.PRODUCT_CATALOG, "CATEGORY"),
                new PermissionActionSeedSpec("Xem Thuộc tính", "ATTRIBUTE_VIEW", PermissionGroup.PRODUCT_CATALOG, "ATTRIBUTE"),
                new PermissionActionSeedSpec("Tạo Thuộc tính", "ATTRIBUTE_CREATE", PermissionGroup.PRODUCT_CATALOG, "ATTRIBUTE"),
                new PermissionActionSeedSpec("Sửa Thuộc tính", "ATTRIBUTE_UPDATE", PermissionGroup.PRODUCT_CATALOG, "ATTRIBUTE"),
                new PermissionActionSeedSpec("Xóa Thuộc tính", "ATTRIBUTE_DELETE", PermissionGroup.PRODUCT_CATALOG, "ATTRIBUTE"),
                new PermissionActionSeedSpec("Xem Khách hàng", "CUSTOMER_VIEW", PermissionGroup.SALES, "CUSTOMER"),
                new PermissionActionSeedSpec("Tạo Khách hàng", "CUSTOMER_CREATE", PermissionGroup.SALES, "CUSTOMER"),
                new PermissionActionSeedSpec("Sửa Khách hàng", "CUSTOMER_UPDATE", PermissionGroup.SALES, "CUSTOMER"),
                new PermissionActionSeedSpec("Xóa Khách hàng", "CUSTOMER_DELETE", PermissionGroup.SALES, "CUSTOMER"),
                new PermissionActionSeedSpec("Xem Voucher", "VOUCHER_VIEW", PermissionGroup.SALES, "VOUCHER"),
                new PermissionActionSeedSpec("Tạo Voucher", "VOUCHER_CREATE", PermissionGroup.SALES, "VOUCHER"),
                new PermissionActionSeedSpec("Sửa Voucher", "VOUCHER_UPDATE", PermissionGroup.SALES, "VOUCHER"),
                new PermissionActionSeedSpec("Xóa Voucher", "VOUCHER_DELETE", PermissionGroup.SALES, "VOUCHER"),
                new PermissionActionSeedSpec("Xem Banner", "BANNER_VIEW", PermissionGroup.SALES, "BANNER"),
                new PermissionActionSeedSpec("Tạo Banner", "BANNER_CREATE", PermissionGroup.SALES, "BANNER"),
                new PermissionActionSeedSpec("Sửa Banner", "BANNER_EDIT", PermissionGroup.SALES, "BANNER"),
                new PermissionActionSeedSpec("Xóa Banner", "BANNER_DELETE", PermissionGroup.SALES, "BANNER"),
                new PermissionActionSeedSpec("Xem Bài viết", "BLOG_VIEW", PermissionGroup.SALES, "BLOG"),
                new PermissionActionSeedSpec("Tạo Bài viết", "BLOG_CREATE", PermissionGroup.SALES, "BLOG"),
                new PermissionActionSeedSpec("Sửa Bài viết", "BLOG_EDIT", PermissionGroup.SALES, "BLOG"),
                new PermissionActionSeedSpec("Xóa Bài viết", "BLOG_DELETE", PermissionGroup.SALES, "BLOG"),
                new PermissionActionSeedSpec("Duyệt Bài viết", "BLOG_APPROVE", PermissionGroup.SALES, "BLOG"),
                new PermissionActionSeedSpec("Xem Chi nhánh", "BRANCH_VIEW", PermissionGroup.SYSTEM, "BRANCH"),
                new PermissionActionSeedSpec("Tạo Chi nhánh", "BRANCH_CREATE", PermissionGroup.SYSTEM, "BRANCH"),
                new PermissionActionSeedSpec("Sửa Chi nhánh", "BRANCH_UPDATE", PermissionGroup.SYSTEM, "BRANCH"),
                new PermissionActionSeedSpec("Xóa Chi nhánh", "BRANCH_DELETE", PermissionGroup.SYSTEM, "BRANCH"),
                new PermissionActionSeedSpec("Xem Nhà cung cấp", "SUPPLIER_VIEW", PermissionGroup.INVENTORY, "SUPPLIER"),
                new PermissionActionSeedSpec("Tạo Nhà cung cấp", "SUPPLIER_CREATE", PermissionGroup.INVENTORY, "SUPPLIER"),
                new PermissionActionSeedSpec("Sửa Nhà cung cấp", "SUPPLIER_UPDATE", PermissionGroup.INVENTORY, "SUPPLIER"),
                new PermissionActionSeedSpec("Xóa Nhà cung cấp", "SUPPLIER_DELETE", PermissionGroup.INVENTORY, "SUPPLIER"),
                new PermissionActionSeedSpec("Xem Tài xế", "DRIVER_VIEW", PermissionGroup.INVENTORY, "DRIVER"),
                new PermissionActionSeedSpec("Tạo Tài xế", "DRIVER_CREATE", PermissionGroup.INVENTORY, "DRIVER"),
                new PermissionActionSeedSpec("Sửa Tài xế", "DRIVER_UPDATE", PermissionGroup.INVENTORY, "DRIVER"),
                new PermissionActionSeedSpec("Xóa Tài xế", "DRIVER_DELETE", PermissionGroup.INVENTORY, "DRIVER"),
                new PermissionActionSeedSpec("Xem Nhập kho", "IMPORT_VIEW", PermissionGroup.INVENTORY, "IMPORT"),
                new PermissionActionSeedSpec("Tạo Nhập kho", "IMPORT_CREATE", PermissionGroup.INVENTORY, "IMPORT"),
                new PermissionActionSeedSpec("Sửa Nhập kho", "IMPORT_UPDATE", PermissionGroup.INVENTORY, "IMPORT"),
                new PermissionActionSeedSpec("Duyệt Nhập kho", "IMPORT_APPROVE", PermissionGroup.INVENTORY, "IMPORT"),
                new PermissionActionSeedSpec("Hủy Nhập kho", "IMPORT_CANCEL", PermissionGroup.INVENTORY, "IMPORT"),
                new PermissionActionSeedSpec("Xóa Nhập kho", "IMPORT_DELETE", PermissionGroup.INVENTORY, "IMPORT"),
                new PermissionActionSeedSpec("Xem Xuất kho", "EXPORT_VIEW", PermissionGroup.INVENTORY, "EXPORT"),
                new PermissionActionSeedSpec("Tạo Xuất kho", "EXPORT_CREATE", PermissionGroup.INVENTORY, "EXPORT"),
                new PermissionActionSeedSpec("Sửa Xuất kho", "EXPORT_UPDATE", PermissionGroup.INVENTORY, "EXPORT"),
                new PermissionActionSeedSpec("Duyệt Xuất kho", "EXPORT_APPROVE", PermissionGroup.INVENTORY, "EXPORT"),
                new PermissionActionSeedSpec("Hủy Xuất kho", "EXPORT_CANCEL", PermissionGroup.INVENTORY, "EXPORT"),
                new PermissionActionSeedSpec("Xóa Xuất kho", "EXPORT_DELETE", PermissionGroup.INVENTORY, "EXPORT"),
                new PermissionActionSeedSpec("Xem Điều chuyển", "TRANSFER_VIEW", PermissionGroup.INVENTORY, "TRANSFER"),
                new PermissionActionSeedSpec("Tạo Điều chuyển", "TRANSFER_CREATE", PermissionGroup.INVENTORY, "TRANSFER"),
                new PermissionActionSeedSpec("Sửa Điều chuyển", "TRANSFER_UPDATE", PermissionGroup.INVENTORY, "TRANSFER"),
                new PermissionActionSeedSpec("Duyệt Điều chuyển", "TRANSFER_APPROVE", PermissionGroup.INVENTORY, "TRANSFER"),
                new PermissionActionSeedSpec("Hủy Điều chuyển", "TRANSFER_CANCEL", PermissionGroup.INVENTORY, "TRANSFER"),
                new PermissionActionSeedSpec("Xóa Điều chuyển", "TRANSFER_DELETE", PermissionGroup.INVENTORY, "TRANSFER"),
                new PermissionActionSeedSpec("Xem Kiểm kê", "INVENTORY_CHECK_VIEW", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                new PermissionActionSeedSpec("Tạo Kiểm kê", "INVENTORY_CHECK_CREATE", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                new PermissionActionSeedSpec("Sửa Kiểm kê", "INVENTORY_CHECK_UPDATE", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                new PermissionActionSeedSpec("Duyệt Kiểm kê", "INVENTORY_CHECK_APPROVE", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                new PermissionActionSeedSpec("Hủy Kiểm kê", "INVENTORY_CHECK_CANCEL", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                new PermissionActionSeedSpec("Xóa Kiểm kê", "INVENTORY_CHECK_DELETE", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                new PermissionActionSeedSpec("Xem Yêu cầu nhập hàng", "PURCHASE_REQUEST_VIEW", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                new PermissionActionSeedSpec("Tạo Yêu cầu nhập hàng", "PURCHASE_REQUEST_CREATE", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                new PermissionActionSeedSpec("Sửa Yêu cầu nhập hàng", "PURCHASE_REQUEST_UPDATE", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                new PermissionActionSeedSpec("Duyệt Yêu cầu nhập hàng", "PURCHASE_REQUEST_APPROVE", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                new PermissionActionSeedSpec("Xóa Yêu cầu nhập hàng", "PURCHASE_REQUEST_DELETE", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                new PermissionActionSeedSpec("Xem Báo cáo doanh thu", "REPORT_REVENUE_VIEW", PermissionGroup.REPORT, "REPORT"),
                new PermissionActionSeedSpec("Xem Báo cáo doanh thu toàn chi nhánh", "REPORT_REVENUE_VIEW_ALL_BRANCHES", PermissionGroup.REPORT, "REPORT"),
                new PermissionActionSeedSpec("Xem Báo cáo tồn kho", "REPORT_INVENTORY_VIEW", PermissionGroup.REPORT, "REPORT"),
                new PermissionActionSeedSpec("Xem Báo cáo tồn kho toàn chi nhánh", "REPORT_INVENTORY_VIEW_ALL_BRANCHES", PermissionGroup.REPORT, "REPORT"),
                new PermissionActionSeedSpec("Xem Báo cáo tài chính", "REPORT_FINANCE_VIEW", PermissionGroup.REPORT, "REPORT"),
                new PermissionActionSeedSpec("Xem Báo cáo tài chính toàn chi nhánh", "REPORT_FINANCE_VIEW_ALL_BRANCHES", PermissionGroup.REPORT, "REPORT"),
                new PermissionActionSeedSpec("Xem Nhân viên", "STAFF_VIEW", PermissionGroup.SYSTEM, "STAFF"),
                new PermissionActionSeedSpec("Tạo Nhân viên", "STAFF_CREATE", PermissionGroup.SYSTEM, "STAFF"),
                new PermissionActionSeedSpec("Sửa Nhân viên", "STAFF_UPDATE", PermissionGroup.SYSTEM, "STAFF"),
                new PermissionActionSeedSpec("Xóa Nhân viên", "STAFF_DELETE", PermissionGroup.SYSTEM, "STAFF"),
                new PermissionActionSeedSpec("Xem Vai trò", "ROLE_VIEW", PermissionGroup.SYSTEM, "ROLE"),
                new PermissionActionSeedSpec("Tạo Vai trò", "ROLE_CREATE", PermissionGroup.SYSTEM, "ROLE"),
                new PermissionActionSeedSpec("Sửa Vai trò", "ROLE_UPDATE", PermissionGroup.SYSTEM, "ROLE"),
                new PermissionActionSeedSpec("Xóa Vai trò", "ROLE_DELETE", PermissionGroup.SYSTEM, "ROLE"),
                new PermissionActionSeedSpec("Xem Cài đặt", "SETTING_VIEW", PermissionGroup.SYSTEM, "SETTING"),
                new PermissionActionSeedSpec("Sửa Cài đặt", "SETTING_UPDATE", PermissionGroup.SYSTEM, "SETTING"),
                new PermissionActionSeedSpec("Xem Tin nhắn", "CHAT_VIEW", PermissionGroup.SYSTEM, "CHAT"),
                new PermissionActionSeedSpec("Quản lý Tin nhắn", "CHAT_MANAGE", PermissionGroup.SYSTEM, "CHAT"),
                new PermissionActionSeedSpec("Xem Nhật ký hoạt động", ACTIVITY_LOG_VIEW_PERMISSION_CODE, PermissionGroup.SYSTEM, ACTIVITY_LOG_MODULE_CODE),
                new PermissionActionSeedSpec("Xem Tri thức AI Doctor", "AI_KNOWLEDGE_VIEW", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                new PermissionActionSeedSpec("Tạo Tri thức AI Doctor", "AI_KNOWLEDGE_CREATE", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                new PermissionActionSeedSpec("Sửa Tri thức AI Doctor", "AI_KNOWLEDGE_UPDATE", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                new PermissionActionSeedSpec("Duyệt Tri thức AI Doctor", "AI_KNOWLEDGE_APPROVE", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                new PermissionActionSeedSpec("Import Tri thức AI Doctor", "AI_IMPORT_KNOWLEDGE", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                new PermissionActionSeedSpec("Duyệt ca bệnh AI", "AI_CASE_REVIEW", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                new PermissionActionSeedSpec("Sử dụng Trợ lý tư vấn", "CUSTOMER_ADVISOR_USE", PermissionGroup.AI_KNOWLEDGE, "CUSTOMER_ADVISOR"),
                new PermissionActionSeedSpec("Sử dụng Không gian kỹ sư", "AGRONOMIST_WORKSPACE_USE", PermissionGroup.AI_KNOWLEDGE, "AGRONOMIST_WORKSPACE"));
    }

    private Set<String> superAdminOnlyPermissionCodes() {
        return Set.of(
                "ROLE",
                "ROLE_VIEW",
                "ROLE_CREATE",
                "ROLE_UPDATE",
                "ROLE_DELETE");
    }

    private Set<Permission> resolveExistingPermissions(
            String roleSlug,
            Set<String> expectedCodes,
            Map<String, Permission> permissionsByCode) {
        Set<Permission> matched = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        for (String code : expectedCodes) {
            Permission p = permissionsByCode.get(code);
            if (p != null) {
                matched.add(p);
            } else {
                missing.add(code);
            }
        }
        if (!missing.isEmpty()) {
            log.warn("Role {} có {} permission không tồn tại trong DB: [{}]",
                    roleSlug,
                    missing.size(),
                    String.join(", ", missing));
        }
        return matched;
    }

    private Role upsertSystemRole(RoleSeedSpec spec, Set<Permission> permissions) {
        Role role = roleRepository.findBySlug(spec.slug())
                .orElseGet(() -> Role.builder()
                        .slug(spec.slug())
                        .isSystem(true)
                        .build());

        role.setDisplayName(spec.displayName());
        role.setDescription(spec.description());
        role.setIsSystem(true);
        role.setIsActive(true);
        role.setPermissions(new HashSet<>(permissions));
        return roleRepository.save(role);
    }

    private void migrateLegacyUserRoleToCustomer(Role customerRole) {
        if (customerRole == null) {
            log.warn("Không tìm thấy role CUSTOMER; bỏ qua migration role USER.");
            return;
        }

        Optional<Role> legacyRoleOpt = roleRepository.findBySlug(LEGACY_USER_ROLE_SLUG);
        if (legacyRoleOpt.isEmpty()) {
            return;
        }

        Role legacyUserRole = legacyRoleOpt.get();
        Set<Permission> legacyPermissions = Optional.ofNullable(legacyUserRole.getPermissions()).orElseGet(Set::of);
        if (!legacyPermissions.isEmpty()) {
            if (customerRole.getPermissions() == null) {
                customerRole.setPermissions(new HashSet<>());
            }
            int before = customerRole.getPermissions().size();
            customerRole.getPermissions().addAll(legacyPermissions);
            Role savedCustomerRole = roleRepository.save(customerRole);
            int added = Math.max(0, Optional.ofNullable(savedCustomerRole.getPermissions()).orElseGet(Set::of).size() - before);
            log.warn("Legacy role USER có {} permission; đã copy {} permission còn thiếu sang CUSTOMER để bảo toàn quyền.",
                    legacyPermissions.size(),
                    added);
        }

        long legacyUserCount = userRepository.countByRole_Slug(LEGACY_USER_ROLE_SLUG);
        if (legacyUserCount > 0) {
            List<User> legacyUsers = userRepository.findAllByRole_Slug(LEGACY_USER_ROLE_SLUG);
            legacyUsers.forEach(user -> user.setRole(customerRole));
            userRepository.saveAll(legacyUsers);
            log.warn("Đã migrate {} tài khoản từ role USER sang CUSTOMER.", legacyUsers.size());
        }

        long remainingLegacyUsers = legacyUserRole.getId() == null
                ? userRepository.countByRole_Slug(LEGACY_USER_ROLE_SLUG)
                : roleRepository.countUsersByRoleId(legacyUserRole.getId());
        if (remainingLegacyUsers == 0) {
            legacyUserRole.setPermissions(new HashSet<>());
            roleRepository.save(legacyUserRole);
            roleRepository.delete(legacyUserRole);
            log.warn("Đã xóa legacy role USER sau khi xác nhận không còn tài khoản FK; permissions đã được bảo toàn trên CUSTOMER.");
        }
    }

    private void bootstrapSuperAdmin(Role superAdminRole) {
        String email = environment.getProperty("BOOTSTRAP_ADMIN_EMAIL", "admin@agrishrimp.vn");
        String password = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD", "123456zoneteam");

        String normalizedEmail = email.trim();
        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
        if (existingUser.isPresent()) {
            User u = existingUser.get();
            if (u.getRole() == null || !"SUPER_ADMIN".equals(u.getRole().getSlug())) {
                u.setRole(superAdminRole);
                userRepository.save(u);
            }
            log.info("Bootstrap SUPER_ADMIN ready: id={}, email={}", u.getId(), normalizedEmail);
            return;
        }

        User bootstrapUser = User.builder()
                .email(normalizedEmail)
                .fullName("Quản trị viên hệ thống (Super Admin)")
                .phoneNumber("0909000001")
                .passwordHash(passwordEncoder.encode(password))
                .status(UserStatus.ACTIVE)
                .role(superAdminRole)
                .gender(Gender.MALE)
                .provider(AuthProvider.LOCAL)
                .addressDetail("Trụ sở chính AgriShrimp")
                .build();

        User savedUser = userRepository.save(bootstrapUser);
        log.info("Bootstrap SUPER_ADMIN user created: id={}, email={}", savedUser.getId(), normalizedEmail);
    }

    private Set<String> codes(String... items) {
        return Set.of(items);
    }

    private record RoleSeedSpec(
            String slug,
            String displayName,
            String description,
            Set<String> permissionCodes) {
    }

    private record PermissionModuleSeedSpec(
            String name,
            String code,
            PermissionGroup groupName) {
    }

    private record PermissionActionSeedSpec(
            String name,
            String code,
            PermissionGroup groupName,
            String parentCode) {
    }
}


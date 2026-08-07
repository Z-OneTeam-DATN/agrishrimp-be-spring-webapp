package com.zone.agri.config;

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
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
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
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryReceiptPaymentRepository inventoryReceiptPaymentRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        boolean hasExistingRoles = roleRepository.count() > 0;

        log.info(">>> ĐỒNG BỘ ROLE HỆ THỐNG VÀ MAPPING PERMISSION HIỆN CÓ...");
        seedSystemRolesAndBootstrapSuperAdmin();

        if (hasExistingRoles) {
            log.info(">>> ĐỒNG BỘ DỮ LIỆU NỀN TẢNG HOÀN TẤT.");
        } else {
            log.info(">>> KHỞI TẠO DỮ LIỆU NỀN TẢNG HOÀN TẤT.");
        }

        // TỰ ĐỘNG SEED DỮ LIỆU MẪU BÁO CÁO TÀI CHÍNH (8 THÁNG)
        seedFinancialDataIfEmpty();
    }

    private void seedFinancialDataIfEmpty() {
        if (orderRepository.count() > 0 && inventoryReceiptPaymentRepository.count() > 0) {
            log.info(">>> DỮ LIỆU BÁO CÁO TÀI CHÍNH ĐÃ TỒN TẠI, BỎ QUA SEED TÀI CHÍNH.");
            return;
        }

        log.info(">>> ĐANG TẠO DỮ LIỆU MẪU CHUẨN CHO BÁO CÁO TÀI CHÍNH (8 THÁNG GẦN NHẤT)...");
        User admin = userRepository.findByEmail("admin@agrishrimp.vn").orElse(null);

        // 1. Chi nhánh (Branches)
        Branch cmBranch = ensureBranch("CN-CM01", "Chi nhánh Cà Mau", "STORE", "02903838388", "camau@agrishrimp.vn", "123 Trần Hưng Đạo, Phường 5, TP. Cà Mau", "Tỉnh Cà Mau", 87);
        Branch stBranch = ensureBranch("CN-ST01", "Chi nhánh Sóc Trăng", "STORE", "02993828288", "soctrang@agrishrimp.vn", "45 Lê Hồng Phong, Phường 3, TP. Sóc Trăng", "Tỉnh Sóc Trăng", 94);
        Branch btBranch = ensureBranch("CN-BT01", "Chi nhánh Bến Tre", "STORE", "02753818188", "bentre@agrishrimp.vn", "88 Nguyễn Đình Chiểu, Phường 2, TP. Bến Tre", "Tỉnh Bến Tre", 83);
        List<Branch> branches = List.of(cmBranch, stBranch, btBranch);

        // 2. Danh mục (Categories)
        Category catFeed = ensureCategory("Thức ăn thủy sản");
        Category catProbiotic = ensureCategory("Men vi sinh & Chế phẩm sinh học");
        Category catMineral = ensureCategory("Khoáng chất & Dinh dưỡng");
        Category catChemical = ensureCategory("Thuốc & Xử lý môi trường nước");
        Category catEquipment = ensureCategory("Thiết bị & Vật tư đầm tôm");

        // 3. Thương hiệu (Brands)
        Brand brandCP = ensureBrand("Tập đoàn C.P. Việt Nam");
        Brand brandGrobest = ensureBrand("Grobest Việt Nam");
        Brand brandTrucAnh = ensureBrand("Trúc Anh Biotech");
        Brand brandBioMar = ensureBrand("BioMar Việt Nam");
        Brand brandShengLong = ensureBrand("Sheng Long Biotech");

        // 4. Nhà cung cấp chuẩn ngành nuôi tôm (Suppliers)
        Supplier supCP = ensureSupplier("NCC-CP", "Công ty TNHH Thức Ăn Thủy Sản C.P. Việt Nam", "0300801234", "Nguyễn Văn Tâm", "0908111222", "sales@cp.com.vn", "KCN Sông Đốc, Cà Mau");
        Supplier supGrobest = ensureSupplier("NCC-GROBEST", "Công ty Cổ phần Grobest Việt Nam", "0301987654", "Lê Thanh Bình", "0908222333", "support@grobest.vn", "KCN An Nghiệp, Sóc Trăng");
        Supplier supTrucAnh = ensureSupplier("NCC-TRUCANH", "Công ty TNHH Sản Xuất & Thương Mại Trúc Anh Biotech", "1900654321", "Đỗ Thị Mai", "0908333444", "trucanh@biotech.vn", "Hiệp Thành, Bạc Liêu");
        Supplier supBioMar = ensureSupplier("NCC-BIOMAR", "Công ty TNHH BioMar Việt Nam", "0302345678", "Trần Quốc Bảo", "0908444555", "info@biomar.vn", "KCN Giao Long, Bến Tre");
        Supplier supShengLong = ensureSupplier("NCC-SHENGLONG", "Công ty TNHH Khoa Kỹ Sinh Học Thăng Long", "0303456789", "Hoàng Văn Long", "0908555666", "shenglong@shenglong.vn", "KCN Đức Hòa, Long An");
        List<Supplier> suppliers = List.of(supCP, supGrobest, supTrucAnh, supBioMar, supShengLong);

        // 5. Sản phẩm & Biến thể (Products & ProductVariants)
        Product pGrobest = ensureProduct("Thức ăn tôm thẻ Grobest Super Premium 1.5mm", "gb-feed-15mm", "Thức ăn tăng trưởng cao cấp cho tôm thẻ chân trắng bao 25kg", catFeed, brandGrobest, supGrobest);
        ProductVariant pvGrobest = ensureVariant(pGrobest, "GB-FEED-15MM");

        Product pCP = ensureProduct("Thức ăn tôm thẻ C.P. 9920 - Bao 25kg", "cp-feed-9920", "Thức ăn đạm cao 40% cho tôm giai đoạn 30-60 ngày", catFeed, brandCP, supCP);
        ProductVariant pvCP = ensureVariant(pCP, "CP-FEED-9920");

        Product pMicro = ensureProduct("Men vi sinh xử lý đáy & nước Trúc Anh Micro-Pro 500g", "ta-micro-500g", "Chế phẩm vi sinh xử lý khí độc NO2/NH3 trong ao tôm", catProbiotic, brandTrucAnh, supTrucAnh);
        ProductVariant pvMicro = ensureVariant(pMicro, "TA-MICRO-500G");

        Product pStomi = ensureProduct("Khoáng tạt tôm thâm canh Stomi K-Mag 5kg", "min-stomi-5kg", "Tăng cường khoáng đa vi lượng, chống cong thân đục cơ", catMineral, brandBioMar, supBioMar);
        ProductVariant pvStomi = ensureVariant(pStomi, "MIN-STOMI-5KG");

        Product pBioBac = ensureProduct("Chế phẩm sinh học xử lý đáy ao BioBac 1kg", "bio-bac-1kg", "Phân hủy mùn bã hữu cơ và bùn đáy ao tôm", catProbiotic, brandShengLong, supShengLong);
        ProductVariant pvBioBac = ensureVariant(pBioBac, "BIO-BAC-1KG");

        Product pBKC = ensureProduct("Dung dịch diệt khuẩn BKC 80% Super 1 Lít", "chem-bkc-1l", "Diệt khuẩn, nấm, protozoa trong nước ao nuôi tôm", catChemical, brandTrucAnh, supTrucAnh);
        ProductVariant pvBKC = ensureVariant(pBKC, "CHEM-BKC-1L");

        Product pFan = ensureProduct("Quạt nước 4 cánh nuôi tôm công suất 2HP", "eq-fan-4b", "Bộ quạt tạo oxy đáy và dòng chảy cho ao tôm thâm canh", catEquipment, brandBioMar, supBioMar);
        ProductVariant pvFan = ensureVariant(pFan, "EQ-FAN-4B");

        Product pFeeder = ensureProduct("Máy cho tôm ăn tự động dung tích 50kg", "eq-feeder-50kg", "Máy phun thức ăn tự động định giờ cho ao tôm", catEquipment, brandBioMar, supBioMar);
        ProductVariant pvFeeder = ensureVariant(pFeeder, "EQ-FEEDER-50KG");

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

        // 6. Tồn kho các chi nhánh (Inventories)
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

        // 7. Khách hàng thực tế (Customers & User accounts)
        List<User> customerUsers = List.of(
                ensureCustomerUser("Nguyễn Văn Hùng", "hung.damdoi@gmail.com", "0918111001", "Trại tôm Đầm Dỗi, Phường 5, TP. Cà Mau", cmBranch),
                ensureCustomerUser("Trần Thị Mỹ Linh", "mylinh.myxuyen@gmail.com", "0918111002", "HTX Thủy sản Mỹ Xuyên, TP. Sóc Trăng", stBranch),
                ensureCustomerUser("Lê Hoàng Nam", "hoangnam.bentre@gmail.com", "0918111003", "Nông hộ nuôi tôm Bến Tre, TP. Bến Tre", btBranch),
                ensureCustomerUser("Phạm Quốc Việt", "viet.batri@gmail.com", "0918111004", "Trại tôm giống Ba Tri, Bến Tre", btBranch),
                ensureCustomerUser("Võ Minh Trí", "tri.triphat@gmail.com", "0918111005", "Đại lý vật tư Trí Phát, Sóc Trăng", stBranch),
                ensureCustomerUser("Đặng Văn Thành", "thanh.duyenhai@gmail.com", "0918111006", "Trại tôm thâm canh Duyên Hải, Cà Mau", cmBranch)
        );

        // 8. Tạo dữ liệu tài chính lịch sử trong 8 tháng liên tục (từ tháng T-8 đến hiện tại)
        LocalDateTime now = LocalDateTime.now();
        int noteSeq = 100;
        int paySeq = 100;
        int orderSeq = 1000;

        for (int m = 8; m >= 0; m--) {
            LocalDateTime monthBase = now.minusMonths(m);
            int daysInMonth = 28;

            // --- A. Phiếu nhập kho từ Nhà cung cấp & Thanh toán công nợ (Sổ quỹ OUT & Công nợ NCC) ---
            for (int i = 0; i < 3; i++) {
                Supplier sup = suppliers.get((m + i) % suppliers.size());
                Branch branch = branches.get((m + i) % branches.size());
                LocalDateTime importDate = monthBase.withDayOfMonth(3 + i * 7).withHour(9).withMinute(30);

                noteSeq++;
                String noteCode = "NK-" + importDate.getYear() + String.format("%02d", importDate.getMonthValue()) + "-" + noteSeq;
                BigDecimal totalAmt = new BigDecimal((60 + (i * 40) + (m * 8)) * 1_000_000L);

                BigDecimal paidAmt;
                if (i == 0) {
                    paidAmt = totalAmt; // Thanh toán đủ
                } else if (i == 1) {
                    paidAmt = totalAmt.multiply(new BigDecimal("0.65")).setScale(2, RoundingMode.HALF_UP); // Trả một phần, còn nợ
                } else {
                    paidAmt = (m == 0) ? BigDecimal.ZERO : totalAmt; // Phiếu gần nhất chưa trả để test công nợ
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

                // Nếu có thanh toán -> tạo bản ghi InventoryReceiptPayment (thanh toán NCC - dòng tiền OUT sổ quỹ)
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

            // --- B. Đơn bán hàng & Giao dịch xuất kho (Sổ quỹ IN, Doanh thu & Giá vốn bán hàng COGS) ---
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

                // Mỗi tháng tạo 1 đơn bị hàng trả (RETURNED) để test mục Hàng bán bị trả lại trong báo cáo Lãi/Lỗ
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

                // Chi tiết sản phẩm đơn hàng
                OrderItem item1 = OrderItem.builder().order(order).productVariant(v1).quantity(q1).price(sellPrices.get(v1)).build();
                OrderItem item2 = OrderItem.builder().order(order).productVariant(v2).quantity(q2).price(sellPrices.get(v2)).build();
                orderItemRepository.saveAll(List.of(item1, item2));

                // Ghi nhận Giao dịch xuất kho (SALE) liên kết referenceCode = orderCode để tính chính xác Giá vốn hàng bán COGS
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

    private Category ensureCategory(String name) {
        return categoryRepository.searchCategories(name, null).stream().findFirst()
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(name)
                        .status(CategoryStatus.ACTIVE)
                        .build()));
    }

    private Brand ensureBrand(String name) {
        return brandRepository.findByName(name)
                .orElseGet(() -> brandRepository.save(Brand.builder()
                        .name(name)
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

    private Product ensureProduct(String name, String slug, String shortDesc, Category category, Brand brand, Supplier supplier) {
        return productRepository.findBySlug(slug)
                .orElseGet(() -> productRepository.save(Product.builder()
                        .name(name)
                        .slug(slug)
                        .shortDesc(shortDesc)
                        .category(category)
                        .brand(brand)
                        .suppliers(new HashSet<>(Set.of(supplier)))
                        .status(ProductStatus.ACTIVE)
                        .createdAt(LocalDateTime.now().minusMonths(9))
                        .build()));
    }

    private ProductVariant ensureVariant(Product product, String sku) {
        return productVariantRepository.findBySku(sku)
                .orElseGet(() -> productVariantRepository.save(ProductVariant.builder()
                        .product(product)
                        .sku(sku)
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
                            .passwordHash(passwordEncoder.encode("123456"))
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

    private void seedSystemRolesAndBootstrapSuperAdmin() {
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
                                "REPORT", "REPORT_REVENUE_VIEW", "REPORT_INVENTORY_VIEW",
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

        for (PermissionModuleSeedSpec spec : systemPermissionModules()) {
            Optional<Permission> existingModule = permissionRepository.findByCode(spec.code());
            Permission module = existingModule
                    .map(permission -> alignPermissionMetadata(
                            permission,
                            spec.name(),
                            spec.groupName(),
                            PermissionType.MODULE,
                            null))
                    .orElseGet(() -> permissionRepository.save(Permission.builder()
                            .name(spec.name())
                            .code(spec.code())
                            .groupName(spec.groupName())
                            .type(PermissionType.MODULE)
                            .build()));
            if (existingModule.isEmpty()) {
                createdModules++;
            }
            modulesByCode.put(spec.code(), module);
        }

        for (PermissionActionSeedSpec spec : systemPermissionActions()) {
            Permission parent = modulesByCode.get(spec.parentCode());
            Long parentId = parent == null ? null : parent.getId();

            Optional<Permission> existingAction = permissionRepository.findByCode(spec.code());
            if (existingAction.isPresent()) {
                alignPermissionMetadata(
                        existingAction.get(),
                        spec.name(),
                        spec.groupName(),
                        PermissionType.ACTION,
                        parentId);
            } else {
                permissionRepository.save(Permission.builder()
                        .name(spec.name())
                        .code(spec.code())
                        .groupName(spec.groupName())
                        .type(PermissionType.ACTION)
                        .parentId(parentId)
                        .build());
                createdActions++;
            }
        }

        log.info("System permissions synchronized: created {} modules, {} actions",
                createdModules,
                createdActions);
    }

    private Permission alignPermissionMetadata(
            Permission permission,
            String name,
            PermissionGroup groupName,
            PermissionType type,
            Long parentId) {
        boolean changed = false;
        if (!name.equals(permission.getName())) {
            permission.setName(name);
            changed = true;
        }
        if (permission.getGroupName() != groupName) {
            permission.setGroupName(groupName);
            changed = true;
        }
        if (permission.getType() != type) {
            permission.setType(type);
            changed = true;
        }
        if (parentId == null ? permission.getParentId() != null : !parentId.equals(permission.getParentId())) {
            permission.setParentId(parentId);
            changed = true;
        }
        return changed ? permissionRepository.save(permission) : permission;
    }

    private List<PermissionModuleSeedSpec> systemPermissionModules() {
        return List.of(
                module("Tổng quan", "DASHBOARD", PermissionGroup.SYSTEM),
                module("Workspace", "WORKSPACE", PermissionGroup.SYSTEM),
                module("Nhật ký hoạt động", ACTIVITY_LOG_MODULE_CODE, PermissionGroup.SYSTEM),
                module("Báo cáo", "REPORT", PermissionGroup.REPORT),
                module("Nhân viên", "STAFF", PermissionGroup.ADMINISTRATION),
                module("Chi nhánh", "BRANCH", PermissionGroup.ADMINISTRATION),
                module("Vai trò", "ROLE", PermissionGroup.ADMINISTRATION),
                module("Đơn hàng", "ORDER", PermissionGroup.SALES),
                module("Khách hàng", "CUSTOMER", PermissionGroup.SALES),
                module("Voucher", "VOUCHER", PermissionGroup.SALES),
                module("Sản phẩm", "PRODUCT", PermissionGroup.PRODUCT_CATALOG),
                module("Danh mục", "CATEGORY", PermissionGroup.PRODUCT_CATALOG),
                module("Thuộc tính", "ATTRIBUTE", PermissionGroup.PRODUCT_CATALOG),
                module("Nhà cung cấp", "SUPPLIER", PermissionGroup.INVENTORY),
                module("Tài xế", "DRIVER", PermissionGroup.INVENTORY),
                module("Nhập hàng", "IMPORT", PermissionGroup.INVENTORY),
                module("Xuất hàng", "EXPORT", PermissionGroup.INVENTORY),
                module("Điều chuyển", "TRANSFER", PermissionGroup.INVENTORY),
                module("Kiểm kê kho", "INVENTORY_CHECK", PermissionGroup.INVENTORY),
                module("Yêu cầu mua NCC", "PURCHASE_REQUEST", PermissionGroup.INVENTORY),
                module("Banner", "BANNER", PermissionGroup.SETTING),
                module("Blog", "BLOG", PermissionGroup.SETTING),
                module("Cài đặt", "SETTING", PermissionGroup.SETTING),
                module("Chat", "CHAT", PermissionGroup.COMMUNICATION),
                module("Tư vấn khách hàng", "CUSTOMER_ADVISOR", PermissionGroup.COMMUNICATION),
                module("Workspace kỹ sư", "AGRONOMIST_WORKSPACE", PermissionGroup.AI_KNOWLEDGE),
                module("Tri thức AI", "AI_KNOWLEDGE", PermissionGroup.AI_KNOWLEDGE));
    }

    private List<PermissionActionSeedSpec> systemPermissionActions() {
        return List.of(
                action("Xem tổng quan", "DASHBOARD_VIEW", PermissionGroup.SYSTEM, "DASHBOARD"),
                action("Xem workspace", "WORKSPACE_VIEW", PermissionGroup.SYSTEM, "WORKSPACE"),
                action("Xem nhật ký", ACTIVITY_LOG_VIEW_PERMISSION_CODE, PermissionGroup.SYSTEM, ACTIVITY_LOG_MODULE_CODE),

                action("Báo cáo doanh thu", "REPORT_REVENUE_VIEW", PermissionGroup.REPORT, "REPORT"),
                action("Báo cáo tồn kho", "REPORT_INVENTORY_VIEW", PermissionGroup.REPORT, "REPORT"),
                action("Tồn kho mọi chi nhánh", "REPORT_INVENTORY_VIEW_ALL_BRANCHES", PermissionGroup.REPORT, "REPORT"),
                action("Báo cáo tài chính", "REPORT_FINANCE_VIEW", PermissionGroup.REPORT, "REPORT"),
                action("Tài chính mọi chi nhánh", "REPORT_FINANCE_VIEW_ALL_BRANCHES", PermissionGroup.REPORT, "REPORT"),

                action("Xem nhân viên", "STAFF_VIEW", PermissionGroup.ADMINISTRATION, "STAFF"),
                action("Thêm nhân viên", "STAFF_CREATE", PermissionGroup.ADMINISTRATION, "STAFF"),
                action("Sửa nhân viên", "STAFF_UPDATE", PermissionGroup.ADMINISTRATION, "STAFF"),
                action("Xóa nhân viên", "STAFF_DELETE", PermissionGroup.ADMINISTRATION, "STAFF"),
                action("Xem chi nhánh", "BRANCH_VIEW", PermissionGroup.ADMINISTRATION, "BRANCH"),
                action("Thêm chi nhánh", "BRANCH_CREATE", PermissionGroup.ADMINISTRATION, "BRANCH"),
                action("Sửa chi nhánh", "BRANCH_UPDATE", PermissionGroup.ADMINISTRATION, "BRANCH"),
                action("Xóa chi nhánh", "BRANCH_DELETE", PermissionGroup.ADMINISTRATION, "BRANCH"),
                action("Xem vai trò", "ROLE_VIEW", PermissionGroup.ADMINISTRATION, "ROLE"),
                action("Tạo vai trò", "ROLE_CREATE", PermissionGroup.ADMINISTRATION, "ROLE"),
                action("Sửa vai trò", "ROLE_UPDATE", PermissionGroup.ADMINISTRATION, "ROLE"),
                action("Xóa vai trò", "ROLE_DELETE", PermissionGroup.ADMINISTRATION, "ROLE"),

                action("Xem đơn hàng", "ORDER_VIEW", PermissionGroup.SALES, "ORDER"),
                action("Tạo đơn hàng", "ORDER_CREATE", PermissionGroup.SALES, "ORDER"),
                action("Cập nhật đơn hàng", "ORDER_UPDATE", PermissionGroup.SALES, "ORDER"),
                action("Xác nhận đơn hàng", "ORDER_CONFIRM", PermissionGroup.SALES, "ORDER"),
                action("Giao hàng", "ORDER_SHIP", PermissionGroup.SALES, "ORDER"),
                action("Hủy đơn hàng", "ORDER_CANCEL", PermissionGroup.SALES, "ORDER"),
                action("Hoàn tất đơn hàng", "ORDER_COMPLETE", PermissionGroup.SALES, "ORDER"),
                action("Xuất DS đơn hàng", "ORDER_EXPORT", PermissionGroup.SALES, "ORDER"),
                action("Hoàn tiền", "ORDER_REFUND", PermissionGroup.SALES, "ORDER"),
                action("Xóa đơn hàng", "ORDER_DELETE", PermissionGroup.SALES, "ORDER"),
                action("Xem khách hàng", "CUSTOMER_VIEW", PermissionGroup.SALES, "CUSTOMER"),
                action("Thêm khách hàng", "CUSTOMER_CREATE", PermissionGroup.SALES, "CUSTOMER"),
                action("Sửa khách hàng", "CUSTOMER_UPDATE", PermissionGroup.SALES, "CUSTOMER"),
                action("Xóa khách hàng", "CUSTOMER_DELETE", PermissionGroup.SALES, "CUSTOMER"),
                action("Xem voucher", "VOUCHER_VIEW", PermissionGroup.SALES, "VOUCHER"),
                action("Thêm voucher", "VOUCHER_CREATE", PermissionGroup.SALES, "VOUCHER"),
                action("Sửa voucher", "VOUCHER_UPDATE", PermissionGroup.SALES, "VOUCHER"),
                action("Xóa voucher", "VOUCHER_DELETE", PermissionGroup.SALES, "VOUCHER"),

                action("Xem sản phẩm", "PRODUCT_VIEW", PermissionGroup.PRODUCT_CATALOG, "PRODUCT"),
                action("Thêm sản phẩm", "PRODUCT_CREATE", PermissionGroup.PRODUCT_CATALOG, "PRODUCT"),
                action("Sửa sản phẩm", "PRODUCT_UPDATE", PermissionGroup.PRODUCT_CATALOG, "PRODUCT"),
                action("Xóa sản phẩm", "PRODUCT_DELETE", PermissionGroup.PRODUCT_CATALOG, "PRODUCT"),
                action("Xem danh mục", "CATEGORY_VIEW", PermissionGroup.PRODUCT_CATALOG, "CATEGORY"),
                action("Thêm danh mục", "CATEGORY_CREATE", PermissionGroup.PRODUCT_CATALOG, "CATEGORY"),
                action("Sửa danh mục", "CATEGORY_UPDATE", PermissionGroup.PRODUCT_CATALOG, "CATEGORY"),
                action("Xóa danh mục", "CATEGORY_DELETE", PermissionGroup.PRODUCT_CATALOG, "CATEGORY"),
                action("Xem thuộc tính", "ATTRIBUTE_VIEW", PermissionGroup.PRODUCT_CATALOG, "ATTRIBUTE"),
                action("Thêm thuộc tính", "ATTRIBUTE_CREATE", PermissionGroup.PRODUCT_CATALOG, "ATTRIBUTE"),
                action("Sửa thuộc tính", "ATTRIBUTE_UPDATE", PermissionGroup.PRODUCT_CATALOG, "ATTRIBUTE"),
                action("Xóa thuộc tính", "ATTRIBUTE_DELETE", PermissionGroup.PRODUCT_CATALOG, "ATTRIBUTE"),

                action("Xem nhà cung cấp", "SUPPLIER_VIEW", PermissionGroup.INVENTORY, "SUPPLIER"),
                action("Thêm nhà cung cấp", "SUPPLIER_CREATE", PermissionGroup.INVENTORY, "SUPPLIER"),
                action("Sửa nhà cung cấp", "SUPPLIER_UPDATE", PermissionGroup.INVENTORY, "SUPPLIER"),
                action("Xóa nhà cung cấp", "SUPPLIER_DELETE", PermissionGroup.INVENTORY, "SUPPLIER"),
                action("Xem tài xế", "DRIVER_VIEW", PermissionGroup.INVENTORY, "DRIVER"),
                action("Thêm tài xế", "DRIVER_CREATE", PermissionGroup.INVENTORY, "DRIVER"),
                action("Sửa tài xế", "DRIVER_UPDATE", PermissionGroup.INVENTORY, "DRIVER"),
                action("Xóa tài xế", "DRIVER_DELETE", PermissionGroup.INVENTORY, "DRIVER"),
                action("Xem phiếu nhập", "IMPORT_VIEW", PermissionGroup.INVENTORY, "IMPORT"),
                action("Tạo phiếu nhập", "IMPORT_CREATE", PermissionGroup.INVENTORY, "IMPORT"),
                action("Sửa phiếu nhập", "IMPORT_UPDATE", PermissionGroup.INVENTORY, "IMPORT"),
                action("Duyệt phiếu nhập", "IMPORT_APPROVE", PermissionGroup.INVENTORY, "IMPORT"),
                action("Hủy phiếu nhập", "IMPORT_CANCEL", PermissionGroup.INVENTORY, "IMPORT"),
                action("Xóa phiếu nhập", "IMPORT_DELETE", PermissionGroup.INVENTORY, "IMPORT"),
                action("Xem phiếu xuất", "EXPORT_VIEW", PermissionGroup.INVENTORY, "EXPORT"),
                action("Tạo phiếu xuất", "EXPORT_CREATE", PermissionGroup.INVENTORY, "EXPORT"),
                action("Duyệt phiếu xuất", "EXPORT_APPROVE", PermissionGroup.INVENTORY, "EXPORT"),
                action("Sửa phiếu xuất", "EXPORT_UPDATE", PermissionGroup.INVENTORY, "EXPORT"),
                action("Hủy phiếu xuất", "EXPORT_CANCEL", PermissionGroup.INVENTORY, "EXPORT"),
                action("Xóa phiếu xuất", "EXPORT_DELETE", PermissionGroup.INVENTORY, "EXPORT"),
                action("Xem điều chuyển", "TRANSFER_VIEW", PermissionGroup.INVENTORY, "TRANSFER"),
                action("Tạo điều chuyển", "TRANSFER_CREATE", PermissionGroup.INVENTORY, "TRANSFER"),
                action("Duyệt điều chuyển", "TRANSFER_APPROVE", PermissionGroup.INVENTORY, "TRANSFER"),
                action("Hủy điều chuyển", "TRANSFER_CANCEL", PermissionGroup.INVENTORY, "TRANSFER"),
                action("Xóa điều chuyển", "TRANSFER_DELETE", PermissionGroup.INVENTORY, "TRANSFER"),
                action("Sửa điều chuyển", "TRANSFER_UPDATE", PermissionGroup.INVENTORY, "TRANSFER"),
                action("Xem kiểm kê", "INVENTORY_CHECK_VIEW", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                action("Tạo kiểm kê", "INVENTORY_CHECK_CREATE", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                action("Duyệt kiểm kê", "INVENTORY_CHECK_APPROVE", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                action("Sửa kiểm kê", "INVENTORY_CHECK_UPDATE", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                action("Hủy kiểm kê", "INVENTORY_CHECK_CANCEL", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                action("Xóa kiểm kê", "INVENTORY_CHECK_DELETE", PermissionGroup.INVENTORY, "INVENTORY_CHECK"),
                action("Xem yêu cầu mua", "PURCHASE_REQUEST_VIEW", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                action("Tạo yêu cầu mua", "PURCHASE_REQUEST_CREATE", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                action("Sửa yêu cầu mua", "PURCHASE_REQUEST_UPDATE", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                action("Duyệt yêu cầu mua", "PURCHASE_REQUEST_APPROVE", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),
                action("Xóa yêu cầu mua", "PURCHASE_REQUEST_DELETE", PermissionGroup.INVENTORY, "PURCHASE_REQUEST"),

                action("Xem banner", "BANNER_VIEW", PermissionGroup.SETTING, "BANNER"),
                action("Tạo banner", "BANNER_CREATE", PermissionGroup.SETTING, "BANNER"),
                action("Sửa banner", "BANNER_EDIT", PermissionGroup.SETTING, "BANNER"),
                action("Xóa banner", "BANNER_DELETE", PermissionGroup.SETTING, "BANNER"),
                action("Xem blog", "BLOG_VIEW", PermissionGroup.SETTING, "BLOG"),
                action("Tạo blog", "BLOG_CREATE", PermissionGroup.SETTING, "BLOG"),
                action("Sửa blog", "BLOG_EDIT", PermissionGroup.SETTING, "BLOG"),
                action("Xóa blog", "BLOG_DELETE", PermissionGroup.SETTING, "BLOG"),
                action("Duyệt blog", "BLOG_APPROVE", PermissionGroup.SETTING, "BLOG"),
                action("Xem cài đặt", "SETTING_VIEW", PermissionGroup.SETTING, "SETTING"),
                action("Cập nhật cài đặt", "SETTING_UPDATE", PermissionGroup.SETTING, "SETTING"),

                action("Xem chat", "CHAT_VIEW", PermissionGroup.COMMUNICATION, "CHAT"),
                action("Quản lý chat", "CHAT_MANAGE", PermissionGroup.COMMUNICATION, "CHAT"),
                action("Dùng tư vấn KH", "CUSTOMER_ADVISOR_USE", PermissionGroup.COMMUNICATION, "CUSTOMER_ADVISOR"),
                action("Dùng workspace kỹ sư", "AGRONOMIST_WORKSPACE_USE", PermissionGroup.AI_KNOWLEDGE, "AGRONOMIST_WORKSPACE"),
                action("Xem tri thức AI", "AI_KNOWLEDGE_VIEW", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                action("Tạo tri thức AI", "AI_KNOWLEDGE_CREATE", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                action("Sửa tri thức AI", "AI_KNOWLEDGE_UPDATE", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                action("Duyệt tri thức AI", "AI_KNOWLEDGE_APPROVE", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                action("Import tri thức AI", "AI_IMPORT_KNOWLEDGE", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"),
                action("Xử lý case AI", "AI_CASE_REVIEW", PermissionGroup.AI_KNOWLEDGE, "AI_KNOWLEDGE"));
    }

    private PermissionModuleSeedSpec module(String name, String code, PermissionGroup groupName) {
        return new PermissionModuleSeedSpec(name, code, groupName);
    }

    private PermissionActionSeedSpec action(String name, String code, PermissionGroup groupName, String parentCode) {
        return new PermissionActionSeedSpec(name, code, groupName, parentCode);
    }

    private Set<String> superAdminOnlyPermissionCodes() {
        return codes("ROLE", "ROLE_VIEW", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE");
    }

    private Set<String> codes(String... permissionCodes) {
        Set<String> codes = new LinkedHashSet<>();
        for (String code : permissionCodes) {
            codes.add(code);
        }
        return codes;
    }

    private Set<Permission> resolveExistingPermissions(
            String roleSlug,
            Set<String> desiredCodes,
            Map<String, Permission> permissionsByCode) {
        Set<Permission> permissions = new LinkedHashSet<>();
        Set<String> missingCodes = new TreeSet<>();
        for (String code : desiredCodes) {
            Permission permission = permissionsByCode.get(code);
            if (permission == null) {
                missingCodes.add(code);
            } else {
                permissions.add(permission);
            }
        }

        if (!missingCodes.isEmpty()) {
            log.warn("Role {} bỏ qua {} permission chưa tồn tại trong DB: {}",
                    roleSlug,
                    missingCodes.size(),
                    String.join(", ", missingCodes));
        }
        return permissions;
    }

    private Role upsertSystemRole(RoleSeedSpec spec, Set<Permission> permissions) {
        return roleRepository.findBySlug(spec.slug())
                .map(existingRole -> {
                    existingRole.setDisplayName(spec.displayName());
                    existingRole.setIsSystem(true);
                    existingRole.setIsActive(true);
                    existingRole.setDescription(spec.description());
                    if (existingRole.getPermissions() == null) {
                        existingRole.setPermissions(new HashSet<>());
                    }
                    int before = existingRole.getPermissions().size();
                    existingRole.getPermissions().addAll(permissions);
                    Role saved = roleRepository.save(existingRole);
                    int after = saved.getPermissions() == null ? 0 : saved.getPermissions().size();
                    log.info("Role {} already exists, added {} missing permissions",
                            spec.slug(),
                            Math.max(0, after - before));
                    logRetainedExtraPermissions(spec, permissions, saved);
                    return saved;
                })
                .orElseGet(() -> {
                    Role role = roleRepository.save(Role.builder()
                            .slug(spec.slug())
                            .displayName(spec.displayName())
                            .isSystem(true)
                            .isActive(true)
                            .description(spec.description())
                            .permissions(new HashSet<>(permissions))
                            .build());
                    log.info("Role {} created", spec.slug());
                    return role;
                });
    }

    private void logRetainedExtraPermissions(RoleSeedSpec spec, Set<Permission> mappedPermissions, Role role) {
        if ("SUPER_ADMIN".equals(spec.slug()) || role.getPermissions() == null) {
            return;
        }

        Set<String> mappedCodes = mappedPermissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> retainedExtraCodes = role.getPermissions().stream()
                .map(Permission::getCode)
                .filter(code -> code != null && !mappedCodes.contains(code))
                .collect(Collectors.toCollection(TreeSet::new));

        if (!retainedExtraCodes.isEmpty()) {
            log.warn("Role {} đang có {} permission ngoài mapping chuẩn; seeder giữ nguyên: {}",
                    spec.slug(),
                    retainedExtraCodes.size(),
                    String.join(", ", retainedExtraCodes));
        }
    }

    private void migrateLegacyUserRoleToCustomer(Role customerRole) {
        if (customerRole == null) {
            log.warn("Không thể migrate legacy role USER: role CUSTOMER chưa sẵn sàng.");
            return;
        }

        Optional<Role> legacyUserRoleOpt = roleRepository.findBySlug(LEGACY_USER_ROLE_SLUG);
        if (legacyUserRoleOpt.isEmpty()) {
            log.info("Legacy role USER không tồn tại trong DB.");
            return;
        }

        Role legacyUserRole = legacyUserRoleOpt.get();
        Set<Permission> legacyPermissions = Optional.ofNullable(legacyUserRole.getPermissions())
                .orElseGet(Set::of);
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
        } else {
            log.info("Không có tài khoản nào đang mang legacy role USER.");
        }

        long remainingLegacyUsers = legacyUserRole.getId() == null
                ? userRepository.countByRole_Slug(LEGACY_USER_ROLE_SLUG)
                : roleRepository.countUsersByRoleId(legacyUserRole.getId());
        if (remainingLegacyUsers == 0) {
            legacyUserRole.setPermissions(new HashSet<>());
            roleRepository.save(legacyUserRole);
            roleRepository.delete(legacyUserRole);
            log.warn("Đã xóa legacy role USER sau khi xác nhận không còn tài khoản FK; permissions đã được bảo toàn trên CUSTOMER.");
        } else {
            log.warn("Legacy role USER vẫn còn {} tài khoản sau migration; cần kiểm tra thủ công.", remainingLegacyUsers);
        }
    }

    private void bootstrapSuperAdmin(Role superAdminRole) {
        Optional<User> existingSuperAdmin = userRepository.findFirstByRole_SlugOrderByIdAsc("SUPER_ADMIN");
        if (existingSuperAdmin.isPresent()) {
            log.info("Bootstrap SUPER_ADMIN skipped: existing user id={} already has SUPER_ADMIN role",
                    existingSuperAdmin.get().getId());
            return;
        }

        String email = environment.getProperty("BOOTSTRAP_ADMIN_EMAIL");
        String password = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Bootstrap SUPER_ADMIN skipped: BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD not configured");
            return;
        }

        String normalizedEmail = email.trim();
        User bootstrapUser = userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> User.builder()
                        .email(normalizedEmail)
                        .fullName("Bootstrap Super Admin")
                        .gender(Gender.MALE)
                        .provider(AuthProvider.LOCAL)
                        .build());

        if (bootstrapUser.getFullName() == null || bootstrapUser.getFullName().isBlank()) {
            bootstrapUser.setFullName("Bootstrap Super Admin");
        }
        bootstrapUser.setEmail(normalizedEmail);
        bootstrapUser.setPasswordHash(passwordEncoder.encode(password));
        bootstrapUser.setStatus(UserStatus.ACTIVE);
        bootstrapUser.setRole(superAdminRole);
        bootstrapUser.setProvider(AuthProvider.LOCAL);
        if (bootstrapUser.getGender() == null) {
            bootstrapUser.setGender(Gender.MALE);
        }

        User savedUser = userRepository.save(bootstrapUser);
        log.info("Bootstrap SUPER_ADMIN user ready: id={}, email={}", savedUser.getId(), normalizedEmail);
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


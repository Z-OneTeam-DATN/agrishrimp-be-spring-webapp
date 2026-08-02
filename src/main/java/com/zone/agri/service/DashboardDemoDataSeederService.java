package com.zone.agri.service;

import com.zone.agri.dto.response.dashboard.DashboardDemoSeedResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryNoteDetail;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.InventoryTransfer;
import com.zone.agri.entity.InventoryTransferDetail;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.OrderItem;
import com.zone.agri.entity.Permission;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.SiteVisit;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.User;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.entity.enums.FulfillmentStatus;
import com.zone.agri.entity.enums.Gender;
import com.zone.agri.entity.enums.InventoryCheckScopeType;
import com.zone.agri.entity.enums.InventoryCheckWorkflowStatus;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.entity.enums.StockStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.entity.enums.VariantStatus;
import com.zone.agri.entity.enums.VoucherDiscountType;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.CategoryRepository;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.PermissionRepository;
import com.zone.agri.repository.ProductRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.SiteVisitRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.repository.VoucherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DashboardDemoDataSeederService {

    private static final String PREFIX = "DEMO-";
    private static final String PREFIX_LIKE = PREFIX + "%";
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final Set<OrderStatus> REVENUE_STATUSES = Set.of(
            OrderStatus.COMPLETED,
            OrderStatus.RECEIVED,
            OrderStatus.SHIPPING);
    private static final Set<OrderStatus> BACKORDER_ACTIVE_STATUSES = Set.of(
            OrderStatus.PENDING,
            OrderStatus.AWAITING_PAYMENT,
            OrderStatus.AWAITING_REPLENISHMENT,
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.SHIPPING,
            OrderStatus.RECEIVED);

    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryTransferRepository inventoryTransferRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final VoucherRepository voucherRepository;
    private final SiteVisitRepository siteVisitRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Idempotency strategy: every run removes old dashboard demo records identified by
     * the {@code DEMO-} prefix in business codes/SKUs and demo user emails, then creates
     * a fresh relative-date data set. Demo branches and roles are reused/upserted because
     * they are access fixtures for comparing branch-scoped dashboard behavior.
     */
    @Transactional
    public DashboardDemoSeedResponse seed() {
        LocalDateTime now = LocalDateTime.now();
        List<String> notes = new ArrayList<>();
        Map<String, Object> deletedRecords = deleteOldDemoData();

        Role customerRole = ensureRole("USER", "Khach hang", Set.of(), false);
        Role managerRole = ensureRole(
                "BRANCH_MANAGER",
                "Quan ly chi nhanh",
                Set.of(
                        "DASHBOARD_VIEW",
                        "REPORT_FINANCE_VIEW",
                        "REPORT_REVENUE_VIEW",
                        "CUSTOMER_VIEW",
                        "ORDER_VIEW",
                        "IMPORT_VIEW",
                        "EXPORT_VIEW",
                        "TRANSFER_VIEW",
                        "INVENTORY_CHECK_VIEW"),
                false);

        BranchSeed branchSeed = ensureBranches();
        Branch branchOne = branchSeed.branches().get(0);
        Branch branchTwo = branchSeed.branches().get(1);

        User manager = createUser(
                "demo.dashboard.manager@agrishrimp.vn",
                "Demo Branch Manager",
                "0992000001",
                "123456",
                managerRole,
                UserStatus.ACTIVE,
                branchOne,
                now.minusDays(15),
                Gender.MALE);
        List<User> customers = createCustomers(customerRole, now);
        addCustomerDistributionNotes(customers, now, notes);
        Voucher voucher = createVoucher(now);

        CatalogSeed catalogSeed = createCatalogAndInventory(List.of(branchOne, branchTwo), now);
        List<InventoryTransaction> inventoryValueTransactions = createInventoryValueGrowthTransactions(
                branchOne,
                manager,
                catalogSeed,
                now);
        List<CreatedOrder> orders = createOrders(
                branchOne,
                branchTwo,
                customers,
                voucher,
                catalogSeed,
                now,
                notes);
        WarehouseSeed warehouseSeed = createWarehouseWorkflowData(branchOne, branchTwo, manager, catalogSeed, now);
        List<SiteVisit> visits = createSiteVisits(now);

        Metrics metrics = buildMetrics(
                orders,
                catalogSeed,
                customers,
                visits,
                warehouseSeed,
                inventoryValueTransactions,
                now,
                notes);

        Map<String, Object> createdRecords = new LinkedHashMap<>();
        createdRecords.put("branchesCreated", branchSeed.createdCount());
        createdRecords.put("branchesUsedForSeeding", branchSeed.branches().size());
        createdRecords.put("categories", catalogSeed.categories().size());
        createdRecords.put("products", catalogSeed.products().size());
        createdRecords.put("variants", catalogSeed.variants().size());
        createdRecords.put("inventoryRows", catalogSeed.inventoriesByKey().size());
        createdRecords.put("customers", customers.size());
        createdRecords.put("managerAccounts", 1);
        createdRecords.put("vouchers", 1);
        createdRecords.put("orders", orders.size());
        createdRecords.put("legacyOrdersWithoutSubOrders", orders.stream().filter(CreatedOrder::legacy).count());
        createdRecords.put("subOrders", orders.stream()
                .filter(order -> !order.legacy())
                .mapToLong(o -> o.slices().size())
                .sum());
        createdRecords.put("orderItems", orders.stream().mapToLong(o -> o.parentLines().size()).sum());
        createdRecords.put("subOrderItems", orders.stream()
                .filter(order -> !order.legacy())
                .flatMap(o -> o.slices().stream())
                .mapToLong(s -> s.lines().size())
                .sum());
        createdRecords.put("saleInventoryTransactions", orders.stream().mapToLong(CreatedOrder::saleTransactionCount).sum());
        createdRecords.put("inventoryValueGrowthTransactions", inventoryValueTransactions.size());
        createdRecords.put("inventoryTransactionsTotal",
                orders.stream().mapToLong(CreatedOrder::saleTransactionCount).sum() + inventoryValueTransactions.size());
        createdRecords.put("inventoryNotes", warehouseSeed.inventoryNotes().size());
        createdRecords.put("inventoryTransfers", warehouseSeed.transfers().size());
        createdRecords.put("siteVisits", visits.size());

        Map<String, Object> accounts = new LinkedHashMap<>();
        accounts.put("superAdmin", Map.of(
                "email", "superadmin@agrishrimp.vn",
                "password", "123456",
                "note", "Tai khoan bootstrap san co tu DataSeeder"));
        accounts.put("branchManager", Map.of(
                "email", manager.getEmail(),
                "password", "123456",
                "role", "BRANCH_MANAGER",
                "branchId", branchOne.getId(),
                "branchName", branchOne.getName(),
                "note", "Bi scope vao chi nhanh nay, khong thay tuy chon Tat ca chi nhanh"));

        notes.add("Khach hang demo co role USER va branch_id = null; dashboard loc theo branch cu the se dem 0 khach hang, dung theo bug da biet trong prompt.");
        notes.add("Don legacy khong co SubOrder van dong gop doanh thu/gia von theo branch cua Order, nhung khong dong gop vao so luong don khi DashboardService dem bang SubOrder theo chi nhanh.");
        notes.add("Top products cua DashboardService lay tu SubOrderItem, nen don legacy khong co SubOrder khong dong gop vao top products.");
        notes.add("Pie chart toan he thong hien lay gross revenue tu OrderItem, pie theo branch lay gross revenue tu SubOrderItem; so nay khong tru voucher.");
        notes.add("Frontend WarehouseWorkflowCards hien tinh exportCommands.length la 'Cho xuat', nen export COMPLETED co the bi tinh trong ca command list va receipt list.");
        notes.add("Backend tra checkWorkflowStatus canonical (vi du PENDING_APPROVAL); neu UI chua map canonical thi card 'Cho duyet lech' co the hien 0 du seed co phieu.");

        return DashboardDemoSeedResponse.builder()
                .message("Da seed du lieu demo dashboard thanh cong")
                .mode("delete-and-recreate")
                .prefix(PREFIX)
                .generatedAt(now)
                .deletedRecords(deletedRecords)
                .createdRecords(createdRecords)
                .demoAccounts(accounts)
                .expectedDashboardNumbers(metrics.expectedDashboardNumbers())
                .branches(metrics.branchSummaries())
                .salesPerformance7Days(metrics.salesPerformance7Days())
                .categoryDistribution(metrics.categoryDistribution())
                .topProducts(metrics.topProducts())
                .orders(toOrderResponse(orders))
                .notes(notes)
                .build();
    }

    private Map<String, Object> deleteOldDemoData() {
        Map<String, Object> deleted = new LinkedHashMap<>();

        deleted.put("inventoryTransactions", executeDelete(
                "DELETE FROM InventoryTransaction tx WHERE tx.referenceCode LIKE :prefix",
                PREFIX_LIKE));
        deleted.put("subOrderItems", executeDelete(
                """
                        DELETE FROM SubOrderItem item
                        WHERE item.subOrder.id IN (
                            SELECT sub.id FROM SubOrder sub WHERE sub.order.code LIKE :prefix
                        )
                        """,
                PREFIX_LIKE));
        deleted.put("subOrders", executeDelete(
                """
                        DELETE FROM SubOrder sub
                        WHERE sub.order.id IN (
                            SELECT o.id FROM Order o WHERE o.code LIKE :prefix
                        )
                        """,
                PREFIX_LIKE));
        deleted.put("orderItems", executeDelete(
                """
                        DELETE FROM OrderItem item
                        WHERE item.order.id IN (
                            SELECT o.id FROM Order o WHERE o.code LIKE :prefix
                        )
                        """,
                PREFIX_LIKE));
        deleted.put("orders", executeDelete(
                "DELETE FROM Order o WHERE o.code LIKE :prefix",
                PREFIX_LIKE));
        deleted.put("inventoryTransferDetails", executeDelete(
                """
                        DELETE FROM InventoryTransferDetail detail
                        WHERE detail.inventoryTransfer.id IN (
                            SELECT t.id FROM InventoryTransfer t WHERE t.transferCode LIKE :prefix
                        )
                        """,
                PREFIX_LIKE));
        deleted.put("inventoryTransfers", executeDelete(
                "DELETE FROM InventoryTransfer t WHERE t.transferCode LIKE :prefix",
                PREFIX_LIKE));
        deleted.put("inventoryNoteDetails", executeDelete(
                """
                        DELETE FROM InventoryNoteDetail detail
                        WHERE detail.inventoryNote.id IN (
                            SELECT n.id FROM InventoryNote n WHERE n.code LIKE :prefix
                        )
                        """,
                PREFIX_LIKE));
        deleted.put("inventoryNotes", executeDelete(
                "DELETE FROM InventoryNote n WHERE n.code LIKE :prefix",
                PREFIX_LIKE));
        deleted.put("siteVisits", executeDelete(
                "DELETE FROM SiteVisit visit WHERE visit.visitorId LIKE :prefix",
                PREFIX_LIKE));
        deleted.put("inventories", executeDelete(
                """
                        DELETE FROM Inventory inv
                        WHERE inv.productVariant.id IN (
                            SELECT variant.id FROM ProductVariant variant WHERE variant.sku LIKE :prefix
                        )
                        """,
                PREFIX_LIKE));
        deleted.put("productVariants", executeDelete(
                "DELETE FROM ProductVariant variant WHERE variant.sku LIKE :prefix",
                PREFIX_LIKE));
        deleted.put("products", executeDelete(
                "DELETE FROM Product p WHERE p.baseSku LIKE :prefix OR p.slug LIKE :slugPrefix",
                Map.of("prefix", PREFIX_LIKE, "slugPrefix", "demo-dashboard-%")));
        deleted.put("categories", executeDelete(
                "DELETE FROM Category c WHERE c.name LIKE :prefix",
                "DEMO - %"));
        deleted.put("vouchers", executeDelete(
                "DELETE FROM Voucher v WHERE v.code LIKE :prefix",
                PREFIX_LIKE));
        deleted.put("users", executeDelete(
                "DELETE FROM User u WHERE u.email LIKE :prefix",
                "demo.dashboard.%@agrishrimp.vn"));
        deleted.put("branches", "reused/upserted, not deleted");
        deleted.put("roles", "reused/upserted, not deleted");

        entityManager.flush();
        entityManager.clear();
        return deleted;
    }

    private int executeDelete(String jpql, String prefix) {
        return entityManager.createQuery(jpql)
                .setParameter("prefix", prefix)
                .executeUpdate();
    }

    private int executeDelete(String jpql, Map<String, Object> params) {
        var query = entityManager.createQuery(jpql);
        params.forEach(query::setParameter);
        return query.executeUpdate();
    }

    private Role ensureRole(String slug, String displayName, Set<String> permissionCodes, boolean system) {
        Set<Permission> permissions = permissionCodes.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(permissionRepository.findAllByCodeIn(new ArrayList<>(permissionCodes)));

        return roleRepository.findBySlug(slug)
                .map(role -> {
                    if (role.getDisplayName() == null || role.getDisplayName().isBlank()) {
                        role.setDisplayName(displayName);
                    }
                    role.setIsActive(true);
                    if (role.getIsSystem() == null) {
                        role.setIsSystem(system);
                    }
                    if (role.getDescription() == null || role.getDescription().isBlank()) {
                        role.setDescription("Vai tro dung cho seed demo dashboard");
                    }
                    if (role.getPermissions() == null) {
                        role.setPermissions(new HashSet<>());
                    }
                    role.getPermissions().addAll(permissions);
                    return roleRepository.save(role);
                })
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .slug(slug)
                        .displayName(displayName)
                        .isActive(true)
                        .isSystem(system)
                        .description("Vai tro dung cho seed demo dashboard")
                        .permissions(permissions)
                        .build()));
    }

    private BranchSeed ensureBranches() {
        List<Branch> activeBranches = new ArrayList<>(branchRepository.findByStatus(BranchStatus.ACTIVE));
        int created = 0;

        List<BranchPlan> branchPlans = List.of(
                new BranchPlan("DEMO-BR-CT", "CN Demo Can Tho", "0991000001", "demo.cantho@agrishrimp.vn",
                        "Kho demo Can Tho", 10.0452, 105.7469),
                new BranchPlan("DEMO-BR-VL", "CN Demo Vinh Long", "0991000002", "demo.vinhlong@agrishrimp.vn",
                        "Kho demo Vinh Long", 10.2537, 105.9722));

        for (BranchPlan plan : branchPlans) {
            if (activeBranches.size() >= 2) {
                break;
            }
            Branch branch = branchRepository.findByBranchCode(plan.code())
                    .map(existing -> {
                        existing.setStatus(BranchStatus.ACTIVE);
                        existing.setName(plan.name());
                        existing.setBranchType("WAREHOUSE");
                        existing.setPhone(plan.phone());
                        existing.setEmail(plan.email());
                        existing.setAddressDetail(plan.address());
                        existing.setFullAddress(plan.address());
                        existing.setMapDisplayName(plan.address());
                        existing.setProvinceName("Demo");
                        existing.setLat(plan.lat());
                        existing.setLng(plan.lng());
                        return branchRepository.save(existing);
                    })
                    .orElseGet(() -> branchRepository.save(Branch.builder()
                            .branchCode(plan.code())
                            .branchType("WAREHOUSE")
                            .name(plan.name())
                            .phone(plan.phone())
                            .email(plan.email())
                            .addressDetail(plan.address())
                            .fullAddress(plan.address())
                            .mapDisplayName(plan.address())
                            .provinceName("Demo")
                            .status(BranchStatus.ACTIVE)
                            .lat(plan.lat())
                            .lng(plan.lng())
                            .build()));
            if (activeBranches.stream().noneMatch(b -> Objects.equals(b.getId(), branch.getId()))) {
                activeBranches.add(branch);
                created++;
            }
        }

        activeBranches = branchRepository.findByStatus(BranchStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(Branch::getId))
                .limit(2)
                .toList();
        if (activeBranches.size() < 2) {
            throw new IllegalStateException("Can it nhat 2 chi nhanh active de seed dashboard demo.");
        }
        return new BranchSeed(activeBranches, created);
    }

    private List<User> createCustomers(Role customerRole, LocalDateTime now) {
        List<User> customers = new ArrayList<>();
        LocalDate yesterday = now.toLocalDate().minusDays(1);
        YearMonth previousMonth = YearMonth.from(now).minusMonths(1);

        List<LocalDateTime> createdAts = List.of(
                todayAt(now, 8, 20),
                todayAt(now, 10, 5),
                todayAt(now, 14, 35),
                todayAt(now, 17, 10),
                atDay(yesterday, 9, 30),
                atDay(yesterday, 12, 15),
                atDay(yesterday, 16, 45),
                currentMonthPastAt(now, 2, 10, 20),
                currentMonthPastAt(now, 4, 15, 40),
                atDay(previousMonth.atDay(Math.min(6, previousMonth.lengthOfMonth())), 9, 0),
                atDay(previousMonth.atDay(Math.min(15, previousMonth.lengthOfMonth())), 13, 20),
                atDay(previousMonth.atDay(Math.min(24, previousMonth.lengthOfMonth())), 16, 30));

        for (int i = 1; i <= createdAts.size(); i++) {
            LocalDateTime createdAt = createdAts.get(i - 1);
            UserStatus status = i <= 8 ? UserStatus.ACTIVE : UserStatus.INACTIVE;
            customers.add(createUser(
                    "demo.dashboard.customer%02d@agrishrimp.vn".formatted(i),
                    "Demo Customer %02d".formatted(i),
                    "09920%05d".formatted(i + 1),
                    "123456",
                    customerRole,
                    status,
                    null,
                    createdAt,
                    i % 2 == 0 ? Gender.FEMALE : Gender.MALE));
        }
        return customers;
    }

    private void addCustomerDistributionNotes(List<User> customers, LocalDateTime now, List<String> notes) {
        LocalDate today = now.toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        YearMonth currentMonth = YearMonth.from(now);
        YearMonth previousMonth = currentMonth.minusMonths(1);

        long todayCount = customers.stream()
                .filter(user -> user.getCreatedAt() != null)
                .filter(user -> user.getCreatedAt().toLocalDate().equals(today))
                .count();
        long yesterdayCount = customers.stream()
                .filter(user -> user.getCreatedAt() != null)
                .filter(user -> user.getCreatedAt().toLocalDate().equals(yesterday))
                .count();
        long currentMonthCount = customers.stream()
                .filter(user -> user.getCreatedAt() != null)
                .filter(user -> YearMonth.from(user.getCreatedAt()).equals(currentMonth))
                .count();
        long previousMonthCount = customers.stream()
                .filter(user -> user.getCreatedAt() != null)
                .filter(user -> YearMonth.from(user.getCreatedAt()).equals(previousMonth))
                .count();

        notes.add("Customer demo: " + customers.size()
                + " ban ghi, active="
                + customers.stream().filter(user -> user.getStatus() == UserStatus.ACTIVE).count()
                + ", inactive="
                + customers.stream().filter(user -> user.getStatus() == UserStatus.INACTIVE).count()
                + ", createdToday=" + todayCount
                + ", createdYesterday=" + yesterdayCount
                + ", createdCurrentMonth=" + currentMonthCount
                + ", createdPreviousMonth=" + previousMonthCount + ".");
    }

    private User createUser(
            String email,
            String fullName,
            String phone,
            String rawPassword,
            Role role,
            UserStatus status,
            Branch branch,
            LocalDateTime createdAt,
            Gender gender) {
        User user = userRepository.saveAndFlush(User.builder()
                .fullName(fullName)
                .email(email)
                .phoneNumber(phone)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(status)
                .role(role)
                .branch(branch)
                .gender(gender)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .provider(AuthProvider.LOCAL)
                .build());
        forceTimestamp("users", user.getId(), createdAt);
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(createdAt);
        return user;
    }

    private Voucher createVoucher(LocalDateTime now) {
        return voucherRepository.save(Voucher.builder()
                .code("DEMO-VOUCHER-10")
                .title("Demo dashboard discount")
                .discountType(VoucherDiscountType.FIXED)
                .value(money(100000))
                .maxDiscount(money(300000))
                .maxUsagePerUser(5)
                .minOrderValue(money(500000))
                .startDate(now.minusMonths(1))
                .endDate(now.plusMonths(1))
                .quantity(999)
                .status(VoucherStatus.ACTIVE)
                .build());
    }

    private CatalogSeed createCatalogAndInventory(List<Branch> branches, LocalDateTime now) {
        List<VariantPlan> plans = List.of(
                new VariantPlan("feed-grow", "DEMO - Thuc an tom", "Thuc an tom sieu tang truong",
                        "demo-dashboard-thuc-an-tang-truong", "DEMO-TA001", "DEMO-TA001-25KG",
                        "Bao 25kg", money(780000), money(520000), 34, 8),
                new VariantPlan("feed-post", "DEMO - Thuc an tom", "Thuc an tom postlarvae",
                        "demo-dashboard-thuc-an-postlarvae", "DEMO-TA002", "DEMO-TA002-10KG",
                        "Bao 10kg", money(450000), money(310000), 12, 0),
                new VariantPlan("pro-water", "DEMO - Men vi sinh", "Men vi sinh xu ly nuoc",
                        "demo-dashboard-men-vi-sinh-nuoc", "DEMO-MV001", "DEMO-MV001-1KG",
                        "Goi 1kg", money(220000), money(130000), 7, 22),
                new VariantPlan("pro-bottom", "DEMO - Men vi sinh", "Che pham vi sinh day ao",
                        "demo-dashboard-vi-sinh-day-ao", "DEMO-MV002", "DEMO-MV002-1KG",
                        "Goi 1kg", money(280000), money(160000), 0, 4),
                new VariantPlan("mineral", "DEMO - Khoang chat", "Khoang tat bo sung canxi",
                        "demo-dashboard-khoang-canxi", "DEMO-KC001", "DEMO-KC001-5KG",
                        "Thung 5kg", money(180000), money(95000), 5, 14),
                new VariantPlan("vitamin", "DEMO - Khoang chat", "Vitamin C tang de khang",
                        "demo-dashboard-vitamin-c", "DEMO-KC002", "DEMO-KC002-500G",
                        "Hop 500g", money(145000), money(70000), 18, 6),
                new VariantPlan("ph-meter", "DEMO - Thiet bi", "May do pH ao nuoi",
                        "demo-dashboard-may-do-ph", "DEMO-TB001", "DEMO-TB001-STD",
                        "Bo tieu chuan", money(650000), money(420000), 2, 16),
                new VariantPlan("water-test", "DEMO - Thiet bi", "Bo test moi truong nuoc",
                        "demo-dashboard-bo-test-nuoc", "DEMO-TB002", "DEMO-TB002-SET",
                        "Bo 6 chi tieu", money(320000), money(190000), 0, 0));

        Map<String, Category> categoryByName = new LinkedHashMap<>();
        for (String categoryName : plans.stream().map(VariantPlan::categoryName).distinct().toList()) {
            Category category = categoryRepository.save(Category.builder()
                    .name(categoryName)
                    .imageUrl("https://dummyimage.com/600x400/e2e8f0/0f172a&text=Demo")
                    .status(CategoryStatus.ACTIVE)
                    .build());
            categoryByName.put(categoryName, category);
        }

        Map<String, DemoVariant> variantsByKey = new LinkedHashMap<>();
        List<Product> products = new ArrayList<>();
        List<ProductVariant> variants = new ArrayList<>();
        int index = 1;
        for (VariantPlan plan : plans) {
            Product product = productRepository.save(Product.builder()
                    .name(plan.productName())
                    .slug(plan.slug())
                    .shortDesc("San pham demo dashboard")
                    .description("Du lieu mau dung de test trang tong quan admin.")
                    .status(ProductStatus.ACTIVE)
                    .createdAt(now.minusDays(20).plusHours(index))
                    .ratingAverage(4.5f)
                    .reviewCount(8 + index)
                    .baseSku(plan.baseSku())
                    .category(categoryByName.get(plan.categoryName()))
                    .build());
            ProductVariant variant = productVariantRepository.save(ProductVariant.builder()
                    .sku(plan.sku())
                    .barcode("893" + String.format("%010d", index))
                    .imageUrl("https://dummyimage.com/600x400/dbeafe/0f172a&text=" + plan.baseSku())
                    .customSpecs(plan.variantName())
                    .status(VariantStatus.ACTIVE)
                    .product(product)
                    .build());

            products.add(product);
            variants.add(variant);
            variantsByKey.put(plan.key(), new DemoVariant(plan, product, variant));
            index++;
        }

        Map<String, Inventory> inventoryByKey = new LinkedHashMap<>();
        for (VariantPlan plan : plans) {
            DemoVariant demoVariant = variantsByKey.get(plan.key());
            for (int branchIndex = 0; branchIndex < branches.size(); branchIndex++) {
                Branch branch = branches.get(branchIndex);
                int quantity = branchIndex == 0 ? plan.branchOneQty() : plan.branchTwoQty();
                Inventory inventory = inventoryRepository.save(Inventory.builder()
                        .quantity(quantity)
                        .defectiveQuantity(plan.key().equals("pro-bottom") && branchIndex == 0 ? 2 : 0)
                        .reservedQuantity(0)
                        .batchNumber("DEMO-BATCH-" + plan.baseSku() + "-B" + (branchIndex + 1))
                        .importPrice(plan.importPrice())
                        .expiryDate(now.plusDays(240 + branchIndex * 30L))
                        .shelfLocation("DEMO-" + (branchIndex + 1) + "-" + plan.baseSku())
                        .lastReceiptDate(now.minusDays(12))
                        .minStock(LOW_STOCK_THRESHOLD)
                        .lastCheckedAt(now.minusDays(2))
                        .branch(branch)
                        .productVariant(demoVariant.variant())
                        .build());
                inventoryByKey.put(inventoryKey(branch, plan.key()), inventory);
            }
        }

        return new CatalogSeed(
                List.copyOf(categoryByName.values()),
                products,
                variants,
                variantsByKey,
                inventoryByKey);
    }

    private List<InventoryTransaction> createInventoryValueGrowthTransactions(
            Branch branch,
            User manager,
            CatalogSeed catalog,
            LocalDateTime now) {
        Inventory inventory = catalog.inventoriesByKey().get(inventoryKey(branch, "feed-grow"));
        int currentBalance = inventory != null && inventory.getQuantity() != null ? inventory.getQuantity() : 0;
        InventoryTransaction transaction = transactionRepository.save(InventoryTransaction.builder()
                .type(TransactionType.IMPORT)
                .quantityChange(40)
                .newBalance(currentBalance + 40)
                .referenceCode(PREFIX + "TX-INV-VALUE-GROWTH")
                .reason("Demo dashboard inventory value growth today")
                .createdAt(todayAt(now, 8, 5))
                .inventory(inventory)
                .createdBy(manager)
                .build());
        return List.of(transaction);
    }

    private List<CreatedOrder> createOrders(
            Branch branchOne,
            Branch branchTwo,
            List<User> customers,
            Voucher voucher,
            CatalogSeed catalog,
            LocalDateTime now,
            List<String> notes) {
        List<CreatedOrder> orders = new ArrayList<>();
        LocalDate today = now.toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);
        LocalDate threeDaysAgo = today.minusDays(3);
        LocalDate fourDaysAgo = today.minusDays(4);
        LocalDate fiveDaysAgo = today.minusDays(5);
        LocalDate sixDaysAgo = today.minusDays(6);
        LocalDate olderThanSeven = today.minusDays(8);
        YearMonth previousMonth = YearMonth.from(now).minusMonths(1);

        if (!YearMonth.from(olderThanSeven).equals(YearMonth.from(now))) {
            notes.add("Ngay " + olderThanSeven + " duoc seed lam don ngoai cua so 7 ngay; neu dang dau thang, ngay nay nam o thang truoc.");
        }

        orders.add(createLegacyOrder(
                "DEMO-O-LGYD",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                atDay(yesterday, 10, 0),
                branchOne,
                customers.get(0),
                voucher,
                money(35000),
                money(100000),
                List.of(line("feed-grow", 1), line("pro-water", 1)),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TD1",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                todayAt(now, 9, 15),
                branchOne,
                customers.get(1),
                voucher,
                money(90000),
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(30000),
                        List.of(line("feed-post", 2), line("mineral", 3)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TD2S",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 13, 30),
                branchOne,
                customers.get(2),
                voucher,
                money(271000),
                List.of(
                        slice(branchOne, OrderStatus.RECEIVED, money(30000),
                                List.of(line("feed-grow", 2), line("pro-water", 1))),
                        slice(branchTwo, OrderStatus.RECEIVED, money(45000),
                                List.of(line("ph-meter", 1), line("pro-bottom", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TD3",
                OrderStatus.SHIPPING,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 16, 0),
                branchTwo,
                customers.get(3),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.SHIPPING, money(25000),
                        List.of(line("water-test", 2), line("vitamin", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TD4",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                todayAt(now, 18, 10),
                branchOne,
                customers.get(10),
                voucher,
                money(300000),
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(50000),
                        List.of(line("ph-meter", 4), line("feed-grow", 3), line("pro-bottom", 5)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TD5",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 18, 35),
                branchTwo,
                customers.get(0),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.RECEIVED, money(20000),
                        List.of(line("mineral", 1), line("pro-water", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TD6",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                todayAt(now, 18, 50),
                branchOne,
                customers.get(1),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(15000),
                        List.of(line("feed-post", 1), line("vitamin", 1)))),
                catalog,
                now));

        addTodayOrderBurst(orders, branchOne, branchTwo, customers, voucher, catalog, now);

        orders.add(createSubOrderOrder(
                "DEMO-O-YD2",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                atDay(yesterday, 15, 30),
                branchTwo,
                customers.get(4),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.RECEIVED, money(20000),
                        List.of(line("pro-bottom", 2), line("mineral", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-YD3",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                atDay(yesterday, 18, 20),
                branchOne,
                customers.get(2),
                voucher,
                money(20000),
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(20000),
                        List.of(line("vitamin", 2), line("water-test", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-D2",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                atDay(twoDaysAgo, 11, 20),
                branchOne,
                customers.get(5),
                voucher,
                money(20000),
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(20000),
                        List.of(line("vitamin", 4), line("pro-water", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-D3",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                atDay(threeDaysAgo, 13, 10),
                branchOne,
                customers.get(11),
                voucher,
                money(30000),
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(20000),
                        List.of(line("feed-post", 1), line("mineral", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-D4",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                atDay(fourDaysAgo, 14, 10),
                branchTwo,
                customers.get(6),
                voucher,
                money(50000),
                List.of(slice(branchTwo, OrderStatus.RECEIVED, money(30000),
                        List.of(line("feed-grow", 1), line("water-test", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-D5",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                atDay(fiveDaysAgo, 10, 25),
                branchTwo,
                customers.get(7),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.RECEIVED, money(25000),
                        List.of(line("pro-water", 2), line("vitamin", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-D6",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                atDay(sixDaysAgo, 15, 15),
                branchOne,
                customers.get(8),
                voucher,
                money(50000),
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(30000),
                        List.of(line("ph-meter", 1), line("water-test", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-OLD7",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                atDay(olderThanSeven, 10, 45),
                branchTwo,
                customers.get(7),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.COMPLETED, money(25000),
                        List.of(line("feed-post", 1), line("vitamin", 3)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-PM1",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                atDay(previousMonth.atDay(Math.min(12, previousMonth.lengthOfMonth())), 9, 40),
                branchOne,
                customers.get(8),
                voucher,
                money(80000),
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(30000),
                        List.of(line("ph-meter", 1), line("feed-post", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-PM2S",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                atDay(previousMonth.atDay(Math.min(20, previousMonth.lengthOfMonth())), 16, 0),
                branchOne,
                customers.get(9),
                voucher,
                money(116000),
                List.of(
                        slice(branchOne, OrderStatus.RECEIVED, money(20000),
                                List.of(line("mineral", 5))),
                        slice(branchTwo, OrderStatus.RECEIVED, money(35000),
                                List.of(line("pro-bottom", 3), line("vitamin", 4)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-PEND",
                OrderStatus.PENDING,
                PaymentStatus.UNPAID,
                PaymentMethod.COD,
                todayAt(now, 10, 15),
                branchOne,
                customers.get(0),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.PENDING, money(15000),
                        List.of(lineMissing("water-test", 3, 1, 2), line("pro-water", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-PAY",
                OrderStatus.AWAITING_PAYMENT,
                PaymentStatus.PENDING,
                PaymentMethod.PAYOS,
                todayAt(now, 11, 5),
                branchTwo,
                customers.get(1),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.AWAITING_PAYMENT, money(25000),
                        List.of(line("feed-grow", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-PROC",
                OrderStatus.PROCESSING,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                atDay(yesterday, 17, 0),
                branchOne,
                customers.get(2),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.PROCESSING, money(20000),
                        List.of(lineMissing("pro-bottom", 2, 1, 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-READY",
                OrderStatus.READY_FOR_PICKUP,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 12, 20),
                branchTwo,
                customers.get(3),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.READY_FOR_PICKUP, money(20000),
                        List.of(line("mineral", 4)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-CANC",
                OrderStatus.CANCELLED,
                PaymentStatus.FAILED,
                PaymentMethod.COD,
                atDay(yesterday, 8, 30),
                branchOne,
                customers.get(4),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.CANCELLED, money(20000),
                        List.of(line("feed-post", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-REPL",
                OrderStatus.AWAITING_REPLENISHMENT,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 15, 5),
                branchTwo,
                customers.get(5),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.AWAITING_REPLENISHMENT, money(15000),
                        List.of(lineMissing("pro-water", 3, 1, 2)))),
                catalog,
                now));

        return orders;
    }

    private void addTodayOrderBurst(
            List<CreatedOrder> orders,
            Branch branchOne,
            Branch branchTwo,
            List<User> customers,
            Voucher voucher,
            CatalogSeed catalog,
            LocalDateTime now) {
        orders.add(createSubOrderOrder(
                "DEMO-O-TDB01",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                todayAt(now, 19, 5),
                branchOne,
                customers.get(3),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(20000),
                        List.of(line("feed-grow", 1), line("mineral", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB02",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 19, 12),
                branchTwo,
                customers.get(4),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.RECEIVED, money(35000),
                        List.of(line("ph-meter", 1), line("vitamin", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB03",
                OrderStatus.SHIPPING,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 19, 19),
                branchOne,
                customers.get(5),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.SHIPPING, money(25000),
                        List.of(line("pro-water", 3), line("water-test", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB04",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                todayAt(now, 19, 26),
                branchTwo,
                customers.get(6),
                voucher,
                money(50000),
                List.of(slice(branchTwo, OrderStatus.COMPLETED, money(30000),
                        List.of(line("feed-grow", 1), line("pro-bottom", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB05S",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 19, 33),
                branchOne,
                customers.get(7),
                voucher,
                money(40000),
                List.of(
                        slice(branchOne, OrderStatus.RECEIVED, money(18000),
                                List.of(line("vitamin", 3))),
                        slice(branchTwo, OrderStatus.RECEIVED, money(22000),
                                List.of(line("mineral", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB06",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                todayAt(now, 19, 40),
                branchOne,
                customers.get(8),
                voucher,
                money(100000),
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(30000),
                        List.of(line("ph-meter", 2), line("water-test", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB07",
                OrderStatus.SHIPPING,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 19, 47),
                branchTwo,
                customers.get(9),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.SHIPPING, money(25000),
                        List.of(line("pro-water", 2), line("vitamin", 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB08",
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                PaymentMethod.TRANSFER,
                todayAt(now, 19, 54),
                branchOne,
                customers.get(10),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.COMPLETED, money(20000),
                        List.of(line("feed-post", 2), line("pro-bottom", 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB09",
                OrderStatus.RECEIVED,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 20, 1),
                branchTwo,
                customers.get(11),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.RECEIVED, money(20000),
                        List.of(line("water-test", 1), line("mineral", 3)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB10",
                OrderStatus.PENDING,
                PaymentStatus.UNPAID,
                PaymentMethod.COD,
                todayAt(now, 20, 8),
                branchOne,
                customers.get(0),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.PENDING, money(20000),
                        List.of(lineMissing("feed-grow", 3, 1, 2)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB11",
                OrderStatus.PROCESSING,
                PaymentStatus.PAID,
                PaymentMethod.COD,
                todayAt(now, 20, 15),
                branchTwo,
                customers.get(1),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchTwo, OrderStatus.PROCESSING, money(30000),
                        List.of(lineMissing("ph-meter", 2, 1, 1)))),
                catalog,
                now));

        orders.add(createSubOrderOrder(
                "DEMO-O-TDB12",
                OrderStatus.AWAITING_PAYMENT,
                PaymentStatus.PENDING,
                PaymentMethod.PAYOS,
                todayAt(now, 20, 22),
                branchOne,
                customers.get(2),
                null,
                BigDecimal.ZERO,
                List.of(slice(branchOne, OrderStatus.AWAITING_PAYMENT, money(20000),
                        List.of(line("pro-water", 2)))),
                catalog,
                now));
    }

    private CreatedOrder createLegacyOrder(
            String code,
            OrderStatus status,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            LocalDateTime createdAt,
            Branch branch,
            User customer,
            Voucher voucher,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            List<OrderLinePlan> lines,
            CatalogSeed catalog,
            LocalDateTime now) {
        BigDecimal subtotal = subtotal(lines, catalog);
        BigDecimal finalAmount = subtotal.add(shippingFee).subtract(discountAmount);
        BigDecimal cost = REVENUE_STATUSES.contains(status) ? cost(lines, catalog) : BigDecimal.ZERO;
        List<CreatedLine> parentLines = toCreatedLines(lines, branch, catalog);
        CreatedSlice slice = new CreatedSlice(
                branch.getId(),
                branch.getName(),
                status,
                subtotal,
                shippingFee,
                discountAmount,
                finalAmount,
                cost,
                totalMissing(lines),
                parentLines);

        Order order = baseOrder(
                code,
                status,
                paymentStatus,
                paymentMethod,
                createdAt,
                branch,
                customer,
                voucher,
                subtotal,
                shippingFee,
                discountAmount,
                finalAmount,
                totalMissing(lines) > 0,
                now);
        order.setOrderItems(parentLines.stream()
                .map(line -> OrderItem.builder()
                        .order(order)
                        .productVariant(catalog.variantsByKey().get(line.variantKey()).variant())
                        .quantity(line.quantity())
                        .price(line.unitPrice())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new)));
        order.setSubOrders(new ArrayList<>());
        Order saved = orderRepository.saveAndFlush(order);

        long saleTransactions = 0;
        if (REVENUE_STATUSES.contains(status)) {
            saleTransactions = saveSaleTransactions(saved.getCode(), createdAt, branch, lines, catalog);
        }

        return new CreatedOrder(
                saved.getCode(),
                status,
                createdAt,
                true,
                branch.getId(),
                branch.getName(),
                subtotal,
                shippingFee,
                discountAmount,
                finalAmount,
                cost,
                List.of(slice),
                parentLines,
                saleTransactions);
    }

    private CreatedOrder createSubOrderOrder(
            String code,
            OrderStatus status,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            LocalDateTime createdAt,
            Branch orderBranch,
            User customer,
            Voucher voucher,
            BigDecimal discountAmount,
            List<SubOrderPlan> subOrderPlans,
            CatalogSeed catalog,
            LocalDateTime now) {
        List<OrderLinePlan> parentLinePlans = subOrderPlans.stream()
                .flatMap(slice -> slice.lines().stream())
                .toList();
        BigDecimal orderSubtotal = subtotal(parentLinePlans, catalog);
        BigDecimal totalShippingFee = subOrderPlans.stream()
                .map(SubOrderPlan::shippingFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalAmount = orderSubtotal.add(totalShippingFee).subtract(discountAmount);
        boolean hasMissing = parentLinePlans.stream().anyMatch(line -> line.missingQuantity() > 0);

        Order order = baseOrder(
                code,
                status,
                paymentStatus,
                paymentMethod,
                createdAt,
                orderBranch,
                customer,
                voucher,
                orderSubtotal,
                totalShippingFee,
                discountAmount,
                finalAmount,
                hasMissing,
                now);

        List<CreatedLine> parentLines = toCreatedLines(parentLinePlans, orderBranch, catalog);
        order.setOrderItems(parentLines.stream()
                .map(line -> OrderItem.builder()
                        .order(order)
                        .productVariant(catalog.variantsByKey().get(line.variantKey()).variant())
                        .quantity(line.quantity())
                        .price(line.unitPrice())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new)));

        List<SubOrder> subOrders = new ArrayList<>();
        List<CreatedSlice> createdSlices = new ArrayList<>();
        for (SubOrderPlan subOrderPlan : subOrderPlans) {
            BigDecimal sliceSubtotal = subtotal(subOrderPlan.lines(), catalog);
            BigDecimal allocatedDiscount = allocateDiscount(sliceSubtotal, orderSubtotal, discountAmount);
            BigDecimal netRevenue = sliceSubtotal.add(subOrderPlan.shippingFee()).subtract(allocatedDiscount);
            BigDecimal sliceCost = REVENUE_STATUSES.contains(subOrderPlan.status())
                    ? cost(subOrderPlan.lines(), catalog)
                    : BigDecimal.ZERO;
            List<CreatedLine> sliceLines = toCreatedLines(subOrderPlan.lines(), subOrderPlan.branch(), catalog);

            SubOrder subOrder = SubOrder.builder()
                    .order(order)
                    .branch(subOrderPlan.branch())
                    .status(subOrderPlan.status())
                    .subtotal(sliceSubtotal)
                    .shippingFee(subOrderPlan.shippingFee())
                    .estimatedDays("2-3 ngay")
                    .carrier("DEMO")
                    .carrierOrderId(PREFIX + "CARRIER-" + code)
                    .trackingCode(PREFIX + "TRK-" + code)
                    .createdAt(createdAt)
                    .updatedAt(createdAt)
                    .receivedAt(receivedAt(subOrderPlan.status(), createdAt, now))
                    .completedAt(completedAt(subOrderPlan.status(), createdAt, now))
                    .cancelledAt(cancelledAt(subOrderPlan.status(), createdAt, now))
                    .items(new ArrayList<>())
                    .build();
            for (OrderLinePlan line : subOrderPlan.lines()) {
                subOrder.getItems().add(SubOrderItem.builder()
                        .subOrder(subOrder)
                        .productVariant(catalog.variantsByKey().get(line.variantKey()).variant())
                        .quantity(line.quantity())
                        .allocatedQuantity(line.allocatedQuantity())
                        .missingQuantity(line.missingQuantity())
                        .unitPrice(catalog.variantsByKey().get(line.variantKey()).plan().sellingPrice())
                        .createdAt(createdAt)
                        .updatedAt(createdAt)
                        .build());
            }
            subOrders.add(subOrder);
            createdSlices.add(new CreatedSlice(
                    subOrderPlan.branch().getId(),
                    subOrderPlan.branch().getName(),
                    subOrderPlan.status(),
                    sliceSubtotal,
                    subOrderPlan.shippingFee(),
                    allocatedDiscount,
                    netRevenue,
                    sliceCost,
                    totalMissing(subOrderPlan.lines()),
                    sliceLines));
        }
        order.setSubOrders(subOrders);
        Order saved = orderRepository.saveAndFlush(order);
        for (SubOrder subOrder : saved.getSubOrders()) {
            forceTimestamp("sub_orders", subOrder.getId(), createdAt);
            if (subOrder.getItems() != null) {
                for (SubOrderItem item : subOrder.getItems()) {
                    forceTimestamp("sub_order_items", item.getId(), createdAt);
                }
            }
        }

        long saleTransactions = 0;
        if (REVENUE_STATUSES.contains(status)) {
            for (SubOrderPlan subOrderPlan : subOrderPlans) {
                if (REVENUE_STATUSES.contains(subOrderPlan.status())) {
                    saleTransactions += saveSaleTransactions(
                            saved.getCode(),
                            createdAt,
                            subOrderPlan.branch(),
                            subOrderPlan.lines(),
                            catalog);
                }
            }
        }

        BigDecimal totalCost = createdSlices.stream()
                .map(CreatedSlice::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CreatedOrder(
                saved.getCode(),
                status,
                createdAt,
                false,
                orderBranch.getId(),
                orderBranch.getName(),
                orderSubtotal,
                totalShippingFee,
                discountAmount,
                finalAmount,
                totalCost,
                createdSlices,
                parentLines,
                saleTransactions);
    }

    private Order baseOrder(
            String code,
            OrderStatus status,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            LocalDateTime createdAt,
            Branch branch,
            User customer,
            Voucher voucher,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            boolean hasMissing,
            LocalDateTime now) {
        return Order.builder()
                .code(code)
                .shippingCode(PREFIX + "SHIP-" + code)
                .totalAmount(subtotal)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .status(status)
                .fulfillmentStatus(fulfillmentStatus(status))
                .stockStatus(hasMissing ? StockStatus.PARTIALLY_AVAILABLE : StockStatus.FULLY_AVAILABLE)
                .autoApproveAt(status == OrderStatus.PENDING ? createdAt.plusHours(2) : null)
                .autoApprovalPaused(false)
                .version(0)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .receivedAt(receivedAt(status, createdAt, now))
                .completedAt(completedAt(status, createdAt, now))
                .cancelledAt(cancelledAt(status, createdAt, now))
                .shippingAddress("Dia chi demo dashboard")
                .totalShippingFee(shippingFee)
                .deliveryAddress("Dia chi demo dashboard")
                .note("Du lieu demo dashboard")
                .receiverName(customer.getFullName())
                .receiverPhone(customer.getPhoneNumber())
                .branch(branch)
                .user(customer)
                .voucher(voucher)
                .build();
    }

    private long saveSaleTransactions(
            String orderCode,
            LocalDateTime createdAt,
            Branch branch,
            List<OrderLinePlan> lines,
            CatalogSeed catalog) {
        long count = 0;
        for (OrderLinePlan line : lines) {
            Inventory inventory = catalog.inventoriesByKey().get(inventoryKey(branch, line.variantKey()));
            int newBalance = inventory != null && inventory.getQuantity() != null ? inventory.getQuantity() : 0;
            transactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.SALE)
                    .quantityChange(-line.quantity())
                    .newBalance(newBalance)
                    .referenceCode(orderCode)
                    .reason("Demo dashboard sale")
                    .createdAt(createdAt)
                    .inventory(inventory)
                    .build());
            count++;
        }
        return count;
    }

    private WarehouseSeed createWarehouseWorkflowData(
            Branch branchOne,
            Branch branchTwo,
            User manager,
            CatalogSeed catalog,
            LocalDateTime now) {
        List<InventoryNote> notes = new ArrayList<>();
        notes.add(createInventoryNote(
                "DEMO-N-IMP-P1",
                InventoryNoteType.IMPORT,
                InventoryNoteStatus.PENDING,
                branchOne,
                null,
                manager,
                now.minusHours(5),
                List.of(noteDetail("feed-grow", 12, money(520000), catalog)),
                null));
        notes.add(createInventoryNote(
                "DEMO-N-IMP-P2",
                InventoryNoteType.IMPORT,
                InventoryNoteStatus.PENDING,
                branchTwo,
                null,
                manager,
                now.minusHours(4),
                List.of(noteDetail("mineral", 8, money(95000), catalog)),
                null));
        notes.add(createInventoryNote(
                "DEMO-N-IMP-C1",
                InventoryNoteType.IMPORT,
                InventoryNoteStatus.COMPLETED,
                branchOne,
                null,
                manager,
                now.minusDays(1),
                List.of(noteDetail("pro-water", 10, money(130000), catalog)),
                null));
        notes.add(createInventoryNote(
                "DEMO-N-EXP-P1",
                InventoryNoteType.EXPORT,
                InventoryNoteStatus.PENDING,
                branchOne,
                null,
                manager,
                now.minusHours(3),
                List.of(noteDetail("pro-bottom", 2, money(160000), catalog)),
                null));
        notes.add(createInventoryNote(
                "DEMO-N-EXP-C1",
                InventoryNoteType.EXPORT,
                InventoryNoteStatus.COMPLETED,
                branchTwo,
                null,
                manager,
                now.minusDays(2),
                List.of(noteDetail("vitamin", 4, money(70000), catalog)),
                null));
        notes.add(createInventoryNote(
                "DEMO-N-CHK-CNT",
                InventoryNoteType.CHECK,
                InventoryNoteStatus.PENDING,
                branchOne,
                null,
                manager,
                now.minusHours(2),
                List.of(noteDetail("feed-post", 12, money(310000), catalog)),
                InventoryCheckWorkflowStatus.COUNTING));
        notes.add(createInventoryNote(
                "DEMO-N-CHK-APP",
                InventoryNoteType.CHECK,
                InventoryNoteStatus.PENDING,
                branchTwo,
                null,
                manager,
                now.minusHours(1),
                List.of(noteDetail("pro-bottom", 4, money(160000), catalog)),
                InventoryCheckWorkflowStatus.PENDING_APPROVAL));
        notes.add(createInventoryNote(
                "DEMO-N-CHK-CMP",
                InventoryNoteType.CHECK,
                InventoryNoteStatus.COMPLETED,
                branchOne,
                null,
                manager,
                now.minusDays(3),
                List.of(noteDetail("ph-meter", 2, money(420000), catalog)),
                InventoryCheckWorkflowStatus.COMPLETED));

        List<InventoryTransfer> transfers = List.of(
                createTransfer("DEMO-T-PEND", InventoryTransferStatus.PENDING, branchOne, branchTwo, manager,
                        now.minusHours(6), List.of(transferDetail("feed-grow", 5, money(560000), catalog))),
                createTransfer("DEMO-T-SHIP", InventoryTransferStatus.SHIPPING, branchTwo, branchOne, manager,
                        now.minusHours(8), List.of(transferDetail("pro-water", 6, money(150000), catalog))),
                createTransfer("DEMO-T-COMP", InventoryTransferStatus.COMPLETED, branchOne, branchTwo, manager,
                        now.minusDays(1), List.of(transferDetail("vitamin", 10, money(90000), catalog))));

        return new WarehouseSeed(notes, transfers);
    }

    private InventoryNote createInventoryNote(
            String code,
            InventoryNoteType type,
            InventoryNoteStatus status,
            Branch branch,
            Branch partnerBranch,
            User createdBy,
            LocalDateTime createdAt,
            List<InventoryNoteDetail> details,
            InventoryCheckWorkflowStatus checkWorkflowStatus) {
        BigDecimal totalAmount = details.stream()
                .map(detail -> safe(detail.getPrice()).multiply(BigDecimal.valueOf(Objects.requireNonNullElse(detail.getQuantity(), 0))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        InventoryNote note = InventoryNote.builder()
                .code(code)
                .type(type)
                .reason("Demo dashboard")
                .status(status)
                .totalAmount(totalAmount)
                .createdAt(createdAt)
                .note("Du lieu demo dashboard")
                .tags("demo,dashboard")
                .deliverer("Demo Seeder")
                .entryDate(createdAt)
                .paymentAmount(status == InventoryNoteStatus.COMPLETED ? totalAmount : BigDecimal.ZERO)
                .debtAmount(status == InventoryNoteStatus.COMPLETED ? BigDecimal.ZERO : totalAmount)
                .shippingAddress("Dia chi demo")
                .checkType(type == InventoryNoteType.CHECK ? "FULL" : null)
                .checkDate(type == InventoryNoteType.CHECK ? createdAt : null)
                .checkedBy(type == InventoryNoteType.CHECK ? createdBy.getFullName() : null)
                .checkScopeType(type == InventoryNoteType.CHECK ? InventoryCheckScopeType.FULL_WAREHOUSE : null)
                .checkWorkflowStatus(checkWorkflowStatus)
                .checkStartedAt(type == InventoryNoteType.CHECK ? createdAt : null)
                .checkSubmittedAt(checkWorkflowStatus == InventoryCheckWorkflowStatus.PENDING_APPROVAL ? createdAt.plusMinutes(30) : null)
                .checkApprovedAt(checkWorkflowStatus == InventoryCheckWorkflowStatus.COMPLETED ? createdAt.plusHours(1) : null)
                .branch(branch)
                .partnerBranch(partnerBranch)
                .createdBy(createdBy)
                .details(new ArrayList<>())
                .build();
        for (InventoryNoteDetail detail : details) {
            detail.setInventoryNote(note);
            note.getDetails().add(detail);
        }
        return inventoryNoteRepository.save(note);
    }

    private InventoryNoteDetail noteDetail(String variantKey, int quantity, BigDecimal price, CatalogSeed catalog) {
        ProductVariant variant = catalog.variantsByKey().get(variantKey).variant();
        return InventoryNoteDetail.builder()
                .quantity(quantity)
                .price(price)
                .quantityRequested(quantity)
                .quantityReal(quantity)
                .quantityAccepted(quantity)
                .quantityRejected(0)
                .batchNumber("DEMO-NOTE-" + variant.getSku())
                .expiryDate(LocalDateTime.now().plusDays(365))
                .note("Demo dashboard")
                .productVariant(variant)
                .build();
    }

    private InventoryTransfer createTransfer(
            String code,
            InventoryTransferStatus status,
            Branch fromBranch,
            Branch toBranch,
            User createdBy,
            LocalDateTime createdAt,
            List<InventoryTransferDetail> details) {
        int totalQuantity = details.stream().mapToInt(InventoryTransferDetail::getQuantity).sum();
        BigDecimal totalValue = details.stream()
                .map(detail -> safe(detail.getUnitTransferPrice()).multiply(BigDecimal.valueOf(detail.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        InventoryTransfer transfer = InventoryTransfer.builder()
                .transferCode(code)
                .status(status)
                .description("Demo dashboard transfer")
                .transferType("BETWEEN_WAREHOUSES")
                .vehicle("Xe tai demo")
                .transporter("Tai xe demo")
                .dispatchOrder(PREFIX + "DISPATCH")
                .referenceCode(PREFIX + "REF-" + code)
                .priority("NORMAL")
                .transferDate(createdAt)
                .deadline(createdAt.plusDays(2))
                .totalValue(totalValue)
                .totalQuantity(totalQuantity)
                .createdAt(createdAt)
                .createdBy(createdBy)
                .createdByBranch(fromBranch)
                .transferBusinessType(TransferBusinessType.STOCK_TRANSFER)
                .transferAmount(BigDecimal.ZERO)
                .settlementStatus(TransferSettlementStatus.PAID)
                .sourceReceivableAmount(BigDecimal.ZERO)
                .destPayableAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .approvedBy(status == InventoryTransferStatus.PENDING ? null : createdBy)
                .approvedAt(status == InventoryTransferStatus.PENDING ? null : createdAt.plusMinutes(30))
                .shippedBy(status == InventoryTransferStatus.SHIPPING || status == InventoryTransferStatus.COMPLETED ? createdBy : null)
                .shippedAt(status == InventoryTransferStatus.SHIPPING || status == InventoryTransferStatus.COMPLETED ? createdAt.plusHours(1) : null)
                .receivedBy(status == InventoryTransferStatus.COMPLETED ? createdBy : null)
                .receivedAt(status == InventoryTransferStatus.COMPLETED ? createdAt.plusHours(3) : null)
                .fromBranch(fromBranch)
                .toBranch(toBranch)
                .sender(createdBy)
                .receiver(createdBy)
                .details(new ArrayList<>())
                .build();
        for (InventoryTransferDetail detail : details) {
            detail.setInventoryTransfer(transfer);
            transfer.getDetails().add(detail);
        }
        return inventoryTransferRepository.save(transfer);
    }

    private InventoryTransferDetail transferDetail(String variantKey, int quantity, BigDecimal unitPrice, CatalogSeed catalog) {
        return InventoryTransferDetail.builder()
                .quantity(quantity)
                .quantityRequested(quantity)
                .quantityReal(quantity)
                .quantityAccepted(quantity)
                .quantityRejected(0)
                .note("Demo dashboard")
                .unitTransferPrice(unitPrice)
                .totalTransferPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .productVariant(catalog.variantsByKey().get(variantKey).variant())
                .build();
    }

    private List<SiteVisit> createSiteVisits(LocalDateTime now) {
        List<String> paths = List.of(
                "/",
                "/san-pham",
                "/san-pham/demo-dashboard-thuc-an-tang-truong",
                "/san-pham/demo-dashboard-men-vi-sinh-nuoc",
                "/blog",
                "/cart");
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        long elapsedMinutes = Math.max(1, Duration.between(startOfToday, now).toMinutes());

        List<SiteVisit> visits = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            String visitorId = PREFIX + "VIS-" + String.format("%02d", (i % 24) + 1);
            LocalDateTime visitedAt = startOfToday.plusMinutes((i * 11L) % elapsedMinutes);
            visits.add(siteVisitRepository.save(SiteVisit.builder()
                    .visitorId(visitorId)
                    .path(paths.get(i % paths.size()))
                    .userAgent(userAgent)
                    .visitedAt(visitedAt)
                    .build()));
        }
        return visits;
    }

    private Metrics buildMetrics(
            List<CreatedOrder> orders,
            CatalogSeed catalog,
            List<User> customers,
            List<SiteVisit> visits,
            WarehouseSeed warehouseSeed,
            List<InventoryTransaction> inventoryValueTransactions,
            LocalDateTime now,
            List<String> notes) {
        Map<String, Object> expected = new LinkedHashMap<>();
        ScopeMetrics allBranches = calculateScopeMetrics(null, orders, catalog, customers, visits, inventoryValueTransactions, now);
        expected.put("allBranches", allBranches.asMap());
        expected.put("warehouseWorkflowCards", warehouseWorkflowCounts(warehouseSeed));
        expected.put("trafficSeededToday", Map.of(
                "pageViews", visits.size(),
                "distinctVisitors", visits.stream().map(SiteVisit::getVisitorId).collect(Collectors.toSet()).size()));
        expected.put("lowStockThreshold", LOW_STOCK_THRESHOLD);

        List<Branch> branches = catalog.inventoriesByKey().values().stream()
                .map(Inventory::getBranch)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Branch::getId, branch -> branch, (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .toList();

        List<Map<String, Object>> branchSummaries = branches.stream()
                .map(branch -> {
                    ScopeMetrics metrics = calculateScopeMetrics(branch.getId(), orders, catalog, customers, visits, inventoryValueTransactions, now);
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("branchId", branch.getId());
                    map.put("branchName", branch.getName());
                    map.putAll(metrics.asMap());
                    return map;
                })
                .toList();

        return new Metrics(
                expected,
                branchSummaries,
                salesPerformance(null, orders, now),
                categoryDistribution(null, orders),
                topProducts(null, orders));
    }

    private ScopeMetrics calculateScopeMetrics(
            Long branchId,
            List<CreatedOrder> orders,
            CatalogSeed catalog,
            List<User> customers,
            List<SiteVisit> visits,
            List<InventoryTransaction> inventoryValueTransactions,
            LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime yesterdayEnd = yesterday.atTime(LocalTime.MAX);
        YearMonth currentMonth = YearMonth.from(now);
        YearMonth previousMonth = currentMonth.minusMonths(1);

        BigDecimal totalRevenue = revenueForScope(branchId, orders, order -> true);
        BigDecimal revenueAsOfYesterday = revenueForScope(branchId, orders, order -> !order.createdAt().isAfter(yesterdayEnd));
        BigDecimal todayRevenue = revenueForScope(branchId, orders, order -> order.createdAt().toLocalDate().equals(today));
        BigDecimal yesterdayRevenue = revenueForScope(branchId, orders, order -> order.createdAt().toLocalDate().equals(yesterday));
        BigDecimal currentMonthRevenue = revenueForScope(branchId, orders, order -> YearMonth.from(order.createdAt()).equals(currentMonth));
        BigDecimal previousMonthRevenue = revenueForScope(branchId, orders, order -> YearMonth.from(order.createdAt()).equals(previousMonth));

        BigDecimal todayCost = costForScope(branchId, orders, order -> order.createdAt().toLocalDate().equals(today));
        BigDecimal yesterdayCost = costForScope(branchId, orders, order -> order.createdAt().toLocalDate().equals(yesterday));
        BigDecimal currentMonthCost = costForScope(branchId, orders, order -> YearMonth.from(order.createdAt()).equals(currentMonth));
        BigDecimal previousMonthCost = costForScope(branchId, orders, order -> YearMonth.from(order.createdAt()).equals(previousMonth));

        long totalOrders = branchId == null
                ? orders.stream().filter(order -> order.status() != OrderStatus.CANCELLED).count()
                : orders.stream()
                        .filter(order -> !order.legacy())
                        .flatMap(order -> order.slices().stream())
                        .filter(slice -> Objects.equals(slice.branchId(), branchId))
                        .filter(slice -> slice.status() != OrderStatus.CANCELLED)
                        .count();
        long ordersAsOfYesterday = branchId == null
                ? orders.stream()
                        .filter(order -> order.status() != OrderStatus.CANCELLED)
                        .filter(order -> !order.createdAt().isAfter(yesterdayEnd))
                        .count()
                : orders.stream()
                        .filter(order -> !order.legacy())
                        .filter(order -> !order.createdAt().isAfter(yesterdayEnd))
                        .flatMap(order -> order.slices().stream())
                        .filter(slice -> Objects.equals(slice.branchId(), branchId))
                        .filter(slice -> slice.status() != OrderStatus.CANCELLED)
                        .count();
        long todayOrders = successOrderCount(branchId, orders, order -> order.createdAt().toLocalDate().equals(today));
        long yesterdayOrders = successOrderCount(branchId, orders, order -> order.createdAt().toLocalDate().equals(yesterday));
        long currentMonthOrders = successOrderCount(branchId, orders, order -> YearMonth.from(order.createdAt()).equals(currentMonth));
        long previousMonthOrders = successOrderCount(branchId, orders, order -> YearMonth.from(order.createdAt()).equals(previousMonth));

        long customerCount = branchId == null ? customers.size() : 0;
        long customersAsOfYesterday = branchId == null
                ? customers.stream()
                        .filter(user -> user.getCreatedAt() != null)
                        .filter(user -> !user.getCreatedAt().isAfter(yesterdayEnd))
                        .count()
                : 0;
        long activeCustomers = branchId == null
                ? customers.stream().filter(user -> user.getStatus() == UserStatus.ACTIVE).count()
                : 0;
        long newCustomersThisMonth = branchId == null
                ? customers.stream()
                        .filter(user -> user.getCreatedAt() != null)
                        .filter(user -> YearMonth.from(user.getCreatedAt()).equals(currentMonth))
                        .count()
                : 0;
        long customersCreatedToday = branchId == null
                ? customers.stream()
                        .filter(user -> user.getCreatedAt() != null)
                        .filter(user -> user.getCreatedAt().toLocalDate().equals(today))
                        .count()
                : 0;
        long customersCreatedYesterday = branchId == null
                ? customers.stream()
                        .filter(user -> user.getCreatedAt() != null)
                        .filter(user -> user.getCreatedAt().toLocalDate().equals(yesterday))
                        .count()
                : 0;
        long customersCreatedPreviousMonth = branchId == null
                ? customers.stream()
                        .filter(user -> user.getCreatedAt() != null)
                        .filter(user -> YearMonth.from(user.getCreatedAt()).equals(previousMonth))
                        .count()
                : 0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalOrders", totalOrders);
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalCustomers", customerCount);
        stats.put("totalProducts", catalog.products().size());
        stats.put("revenueAsOfYesterday", revenueAsOfYesterday);
        stats.put("revenueChangePercent", growth(totalRevenue, revenueAsOfYesterday));
        stats.put("revenueIsNew", isNew(totalRevenue, revenueAsOfYesterday));
        stats.put("ordersAsOfYesterday", ordersAsOfYesterday);
        stats.put("ordersChangePercent", growth(totalOrders, ordersAsOfYesterday));
        stats.put("ordersIsNew", isNew(totalOrders, ordersAsOfYesterday));
        stats.put("customersAsOfYesterday", customersAsOfYesterday);
        stats.put("customersChangePercent", growth(customerCount, customersAsOfYesterday));
        stats.put("customersIsNew", isNew(customerCount, customersAsOfYesterday));

        Map<String, Object> customerInsights = new LinkedHashMap<>();
        customerInsights.put("totalCustomers", customerCount);
        customerInsights.put("activeCustomers", activeCustomers);
        customerInsights.put("newCustomersThisMonth", newCustomersThisMonth);
        customerInsights.put("createdToday", customersCreatedToday);
        customerInsights.put("createdYesterday", customersCreatedYesterday);
        customerInsights.put("createdPreviousMonth", customersCreatedPreviousMonth);
        customerInsights.put("todayVisitors", visits.stream().map(SiteVisit::getVisitorId).collect(Collectors.toSet()).size());
        customerInsights.put("todayPageViews", visits.size());

        Map<String, Object> dailyResults = new LinkedHashMap<>();
        dailyResults.put("todayRevenue", todayRevenue);
        dailyResults.put("yesterdayRevenue", yesterdayRevenue);
        dailyResults.put("revenueChangePercent", growth(todayRevenue, yesterdayRevenue));
        dailyResults.put("revenueIsNew", isNew(todayRevenue, yesterdayRevenue));
        dailyResults.put("todayProfit", todayRevenue.subtract(todayCost));
        dailyResults.put("yesterdayProfit", yesterdayRevenue.subtract(yesterdayCost));
        dailyResults.put("profitChangePercent", growth(todayRevenue.subtract(todayCost), yesterdayRevenue.subtract(yesterdayCost)));
        dailyResults.put("profitIsNew", isNew(todayRevenue.subtract(todayCost), yesterdayRevenue.subtract(yesterdayCost)));
        dailyResults.put("todayOrders", todayOrders);
        dailyResults.put("yesterdayOrders", yesterdayOrders);
        dailyResults.put("orderChangePercent", growth(todayOrders, yesterdayOrders));
        dailyResults.put("orderIsNew", isNew(todayOrders, yesterdayOrders));

        Map<String, Object> monthlyResults = new LinkedHashMap<>();
        monthlyResults.put("yearMonth", currentMonth.toString());
        monthlyResults.put("currentMonthRevenue", currentMonthRevenue);
        monthlyResults.put("previousMonthRevenue", previousMonthRevenue);
        monthlyResults.put("revenueChangePercent", growth(currentMonthRevenue, previousMonthRevenue));
        monthlyResults.put("revenueIsNew", isNew(currentMonthRevenue, previousMonthRevenue));
        monthlyResults.put("currentMonthProfit", currentMonthRevenue.subtract(currentMonthCost));
        monthlyResults.put("previousMonthProfit", previousMonthRevenue.subtract(previousMonthCost));
        monthlyResults.put("profitChangePercent", growth(currentMonthRevenue.subtract(currentMonthCost), previousMonthRevenue.subtract(previousMonthCost)));
        monthlyResults.put("profitIsNew", isNew(currentMonthRevenue.subtract(currentMonthCost), previousMonthRevenue.subtract(previousMonthCost)));
        monthlyResults.put("currentMonthOrders", currentMonthOrders);
        monthlyResults.put("previousMonthOrders", previousMonthOrders);
        monthlyResults.put("orderChangePercent", growth(currentMonthOrders, previousMonthOrders));
        monthlyResults.put("orderIsNew", isNew(currentMonthOrders, previousMonthOrders));

        Map<String, Object> summary = pendingSummary(branchId, orders);
        Map<String, Object> inventory = inventoryInfo(branchId, catalog, orders, inventoryValueTransactions, now);
        Map<String, Object> backorders = backorders(branchId, orders);

        return new ScopeMetrics(stats, customerInsights, dailyResults, monthlyResults, summary, inventory, backorders);
    }

    private BigDecimal revenueForScope(Long branchId, List<CreatedOrder> orders, Predicate<CreatedOrder> orderFilter) {
        BigDecimal total = BigDecimal.ZERO;
        for (CreatedOrder order : orders) {
            if (!orderFilter.test(order) || !REVENUE_STATUSES.contains(order.status())) {
                continue;
            }
            if (branchId == null) {
                total = total.add(order.netRevenue());
            } else if (order.legacy()) {
                if (Objects.equals(order.orderBranchId(), branchId)) {
                    total = total.add(order.netRevenue());
                }
            } else {
                total = total.add(order.slices().stream()
                        .filter(slice -> Objects.equals(slice.branchId(), branchId))
                        .filter(slice -> REVENUE_STATUSES.contains(slice.status()))
                        .map(CreatedSlice::netRevenue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
        return total;
    }

    private BigDecimal costForScope(Long branchId, List<CreatedOrder> orders, Predicate<CreatedOrder> orderFilter) {
        BigDecimal total = BigDecimal.ZERO;
        for (CreatedOrder order : orders) {
            if (!orderFilter.test(order) || !REVENUE_STATUSES.contains(order.status())) {
                continue;
            }
            if (branchId == null) {
                total = total.add(order.cost());
            } else {
                total = total.add(order.slices().stream()
                        .filter(slice -> Objects.equals(slice.branchId(), branchId))
                        .filter(slice -> REVENUE_STATUSES.contains(slice.status()))
                        .map(CreatedSlice::cost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
        return total;
    }

    private long successOrderCount(Long branchId, List<CreatedOrder> orders, Predicate<CreatedOrder> orderFilter) {
        if (branchId == null) {
            return orders.stream()
                    .filter(orderFilter)
                    .filter(order -> REVENUE_STATUSES.contains(order.status()))
                    .count();
        }
        return orders.stream()
                .filter(orderFilter)
                .filter(order -> !order.legacy())
                .flatMap(order -> order.slices().stream())
                .filter(slice -> Objects.equals(slice.branchId(), branchId))
                .filter(slice -> REVENUE_STATUSES.contains(slice.status()))
                .count();
    }

    private Map<String, Object> pendingSummary(Long branchId, List<CreatedOrder> orders) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        if (branchId == null) {
            for (CreatedOrder order : orders) {
                counts.merge(order.status(), 1L, Long::sum);
            }
        } else {
            for (CreatedOrder order : orders) {
                if (order.legacy()) {
                    continue;
                }
                for (CreatedSlice slice : order.slices()) {
                    if (Objects.equals(slice.branchId(), branchId)) {
                        counts.merge(slice.status(), 1L, Long::sum);
                    }
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pendingApproval", counts.getOrDefault(OrderStatus.PENDING, 0L));
        summary.put("pendingPayment", counts.getOrDefault(OrderStatus.AWAITING_PAYMENT, 0L));
        summary.put("pendingPacking", counts.getOrDefault(OrderStatus.PROCESSING, 0L));
        summary.put("pendingPickup", counts.getOrDefault(OrderStatus.READY_FOR_PICKUP, 0L));
        summary.put("shipping", counts.getOrDefault(OrderStatus.SHIPPING, 0L));
        summary.put("cancelPending", counts.getOrDefault(OrderStatus.CANCELLED, 0L));
        return summary;
    }

    private Map<String, Object> inventoryInfo(
            Long branchId,
            CatalogSeed catalog,
            List<CreatedOrder> orders,
            List<InventoryTransaction> inventoryValueTransactions,
            LocalDateTime now) {
        Map<Long, Integer> quantityByProduct = new HashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Inventory inventory : catalog.inventoriesByKey().values()) {
            if (branchId != null && !Objects.equals(inventory.getBranch().getId(), branchId)) {
                continue;
            }
            Long productId = inventory.getProductVariant().getProduct().getId();
            int quantity = Objects.requireNonNullElse(inventory.getQuantity(), 0);
            quantityByProduct.merge(productId, quantity, Integer::sum);
            totalValue = totalValue.add(BigDecimal.valueOf(quantity).multiply(safe(inventory.getImportPrice())));
        }

        Set<Long> allProductIds = catalog.products().stream().map(Product::getId).collect(Collectors.toSet());
        long lowStock = quantityByProduct.values().stream()
                .filter(quantity -> quantity > 0 && quantity <= LOW_STOCK_THRESHOLD)
                .count();
        long outOfStock = allProductIds.stream()
                .filter(productId -> quantityByProduct.getOrDefault(productId, 0) <= 0)
                .count();
        long stableStock = quantityByProduct.values().stream()
                .filter(quantity -> quantity > LOW_STOCK_THRESHOLD)
                .count();
        BigDecimal netValueChangeToday = netInventoryValueChangeToday(branchId, orders, inventoryValueTransactions, now.toLocalDate());
        BigDecimal valueAsOfYesterday = totalValue.subtract(netValueChangeToday);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("totalItems", allProductIds.size());
        info.put("stableStockCount", stableStock);
        info.put("lowStockCount", lowStock);
        info.put("outOfStockCount", outOfStock);
        info.put("totalInventoryValue", totalValue);
        info.put("netValueChangeToday", netValueChangeToday);
        info.put("valueAsOfYesterday", valueAsOfYesterday);
        info.put("valueChangePercent", growth(totalValue, valueAsOfYesterday));
        info.put("valueIsNew", isNew(totalValue, valueAsOfYesterday));
        return info;
    }

    private BigDecimal netInventoryValueChangeToday(
            Long branchId,
            List<CreatedOrder> orders,
            List<InventoryTransaction> inventoryValueTransactions,
            LocalDate today) {
        BigDecimal saleChange = costForScope(branchId, orders, order -> order.createdAt().toLocalDate().equals(today)).negate();
        BigDecimal manualChange = inventoryValueTransactions.stream()
                .filter(tx -> tx.getCreatedAt() != null && tx.getCreatedAt().toLocalDate().equals(today))
                .filter(tx -> tx.getInventory() != null)
                .filter(tx -> branchId == null || Objects.equals(tx.getInventory().getBranch().getId(), branchId))
                .map(tx -> safe(tx.getInventory().getImportPrice())
                        .multiply(BigDecimal.valueOf(Objects.requireNonNullElse(tx.getQuantityChange(), 0))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return saleChange.add(manualChange);
    }

    private Map<String, Object> backorders(Long branchId, List<CreatedOrder> orders) {
        long affectedSubOrders = 0;
        long totalMissing = 0;
        Map<String, Long> missingBySku = new LinkedHashMap<>();

        for (CreatedOrder order : orders) {
            for (CreatedSlice slice : order.slices()) {
                if (branchId != null && !Objects.equals(slice.branchId(), branchId)) {
                    continue;
                }
                if (!BACKORDER_ACTIVE_STATUSES.contains(slice.status())) {
                    continue;
                }
                long sliceMissing = slice.lines().stream().mapToLong(CreatedLine::missingQuantity).sum();
                if (sliceMissing <= 0) {
                    continue;
                }
                affectedSubOrders++;
                totalMissing += sliceMissing;
                for (CreatedLine line : slice.lines()) {
                    if (line.missingQuantity() > 0) {
                        missingBySku.merge(line.sku(), (long) line.missingQuantity(), Long::sum);
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("affectedSubOrders", affectedSubOrders);
        result.put("totalMissingQuantity", totalMissing);
        result.put("missingBySku", missingBySku);
        return result;
    }

    private List<Map<String, Object>> salesPerformance(Long branchId, List<CreatedOrder> orders, LocalDateTime now) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = now.toLocalDate().minusDays(i);
            BigDecimal revenue = revenueForScope(branchId, orders, order -> order.createdAt().toLocalDate().equals(date));
            BigDecimal cost = costForScope(branchId, orders, order -> order.createdAt().toLocalDate().equals(date));
            long count = successOrderCount(branchId, orders, order -> order.createdAt().toLocalDate().equals(date));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            row.put("revenue", revenue);
            row.put("profit", revenue.subtract(cost));
            row.put("orderCount", count);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> categoryDistribution(Long branchId, List<CreatedOrder> orders) {
        Map<String, CategoryMetric> metrics = new LinkedHashMap<>();
        for (CreatedOrder order : orders) {
            if (!REVENUE_STATUSES.contains(order.status())) {
                continue;
            }
            if (branchId == null) {
                for (CreatedLine line : order.parentLines()) {
                    addCategoryMetric(metrics, line);
                }
            } else {
                if (order.legacy()) {
                    continue;
                }
                for (CreatedSlice slice : order.slices()) {
                    if (Objects.equals(slice.branchId(), branchId) && REVENUE_STATUSES.contains(slice.status())) {
                        for (CreatedLine line : slice.lines()) {
                            addCategoryMetric(metrics, line);
                        }
                    }
                }
            }
        }
        BigDecimal totalRevenue = metrics.values().stream()
                .map(CategoryMetric::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return metrics.values().stream()
                .sorted(Comparator.comparing(CategoryMetric::revenue).reversed())
                .map(metric -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("categoryName", metric.categoryName());
                    map.put("totalRevenue", metric.revenue());
                    map.put("totalQuantity", metric.quantity());
                    map.put("percentage", totalRevenue.compareTo(BigDecimal.ZERO) == 0
                            ? 0.0
                            : metric.revenue().multiply(BigDecimal.valueOf(100))
                                    .divide(totalRevenue, 2, RoundingMode.HALF_UP)
                                    .doubleValue());
                    return map;
                })
                .toList();
    }

    private List<Map<String, Object>> topProducts(Long branchId, List<CreatedOrder> orders) {
        Map<String, ProductMetric> metrics = new LinkedHashMap<>();
        for (CreatedOrder order : orders) {
            if (order.legacy()) {
                continue;
            }
            for (CreatedSlice slice : order.slices()) {
                if (!REVENUE_STATUSES.contains(slice.status())) {
                    continue;
                }
                if (branchId != null && !Objects.equals(slice.branchId(), branchId)) {
                    continue;
                }
                for (CreatedLine line : slice.lines()) {
                    ProductMetric current = metrics.get(line.productName());
                    BigDecimal lineRevenue = line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
                    if (current == null) {
                        metrics.put(line.productName(), new ProductMetric(line.productName(), line.quantity(), lineRevenue));
                    } else {
                        metrics.put(line.productName(), new ProductMetric(
                                line.productName(),
                                current.quantity() + line.quantity(),
                                current.revenue().add(lineRevenue)));
                    }
                }
            }
        }
        return metrics.values().stream()
                .sorted(Comparator.comparing(ProductMetric::quantity).reversed())
                .limit(5)
                .map(metric -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("productName", metric.productName());
                    map.put("quantitySold", metric.quantity());
                    map.put("revenue", metric.revenue());
                    return map;
                })
                .toList();
    }

    private Map<String, Object> warehouseWorkflowCounts(WarehouseSeed seed) {
        long pendingReceipts = seed.inventoryNotes().stream()
                .filter(note -> note.getType() == InventoryNoteType.IMPORT)
                .filter(note -> note.getStatus() == InventoryNoteStatus.PENDING)
                .count();
        long completedReceipts = seed.inventoryNotes().stream()
                .filter(note -> note.getType() == InventoryNoteType.IMPORT)
                .filter(note -> note.getStatus() == InventoryNoteStatus.COMPLETED)
                .count();
        long exportCommandsUiCount = seed.inventoryNotes().stream()
                .filter(note -> note.getType() == InventoryNoteType.EXPORT)
                .filter(note -> Set.of(
                        InventoryNoteStatus.PENDING,
                        InventoryNoteStatus.APPROVED,
                        InventoryNoteStatus.COMPLETED,
                        InventoryNoteStatus.REJECTED,
                        InventoryNoteStatus.CANCELLED).contains(note.getStatus()))
                .count();
        long completedExports = seed.inventoryNotes().stream()
                .filter(note -> note.getType() == InventoryNoteType.EXPORT)
                .filter(note -> note.getStatus() == InventoryNoteStatus.COMPLETED)
                .count();
        long pendingTransfers = seed.transfers().stream()
                .filter(transfer -> transfer.getStatus() == InventoryTransferStatus.PENDING)
                .count();
        long shippingTransfers = seed.transfers().stream()
                .filter(transfer -> transfer.getStatus() == InventoryTransferStatus.SHIPPING)
                .count();
        long completedTransfers = seed.transfers().stream()
                .filter(transfer -> transfer.getStatus() == InventoryTransferStatus.COMPLETED)
                .count();
        long countingChecks = seed.inventoryNotes().stream()
                .filter(note -> note.getType() == InventoryNoteType.CHECK)
                .filter(note -> note.getCheckWorkflowStatus() == InventoryCheckWorkflowStatus.COUNTING
                        || note.getCheckWorkflowStatus() == InventoryCheckWorkflowStatus.DRAFT)
                .count();
        long waitingApprovalChecks = seed.inventoryNotes().stream()
                .filter(note -> note.getType() == InventoryNoteType.CHECK)
                .filter(note -> note.getCheckWorkflowStatus() == InventoryCheckWorkflowStatus.PENDING_APPROVAL)
                .count();
        long completedChecks = seed.inventoryNotes().stream()
                .filter(note -> note.getType() == InventoryNoteType.CHECK)
                .filter(note -> note.getCheckWorkflowStatus() == InventoryCheckWorkflowStatus.COMPLETED)
                .count();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("receipts", Map.of("pending", pendingReceipts, "completed", completedReceipts));
        counts.put("exports", Map.of(
                "exportCommandsUiCount", exportCommandsUiCount,
                "completedReceipts", completedExports));
        counts.put("transfers", Map.of(
                "pending", pendingTransfers,
                "shipping", shippingTransfers,
                "completed", completedTransfers));
        counts.put("inventoryChecksSeededCanonical", Map.of(
                "counting", countingChecks,
                "pendingApproval", waitingApprovalChecks,
                "completed", completedChecks));
        return counts;
    }

    private List<Map<String, Object>> toOrderResponse(List<CreatedOrder> orders) {
        return orders.stream()
                .map(order -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("code", order.code());
                    map.put("status", order.status());
                    map.put("createdAt", order.createdAt());
                    map.put("legacyWithoutSubOrders", order.legacy());
                    map.put("orderBranchName", order.orderBranchName());
                    map.put("totalAmount", order.totalAmount());
                    map.put("totalShippingFee", order.shippingFee());
                    map.put("discountAmount", order.discountAmount());
                    map.put("netRevenueIfSuccessful", REVENUE_STATUSES.contains(order.status()) ? order.netRevenue() : BigDecimal.ZERO);
                    map.put("costIfSuccessful", REVENUE_STATUSES.contains(order.status()) ? order.cost() : BigDecimal.ZERO);
                    map.put("subOrders", order.slices().stream().map(slice -> {
                        Map<String, Object> sliceMap = new LinkedHashMap<>();
                        sliceMap.put("branchName", slice.branchName());
                        sliceMap.put("status", slice.status());
                        sliceMap.put("subtotal", slice.subtotal());
                        sliceMap.put("shippingFee", slice.shippingFee());
                        sliceMap.put("allocatedDiscount", slice.allocatedDiscount());
                        sliceMap.put("netRevenueIfSuccessful", REVENUE_STATUSES.contains(slice.status()) ? slice.netRevenue() : BigDecimal.ZERO);
                        sliceMap.put("costIfSuccessful", REVENUE_STATUSES.contains(slice.status()) ? slice.cost() : BigDecimal.ZERO);
                        sliceMap.put("missingQuantity", slice.missingQuantity());
                        sliceMap.put("items", slice.lines().stream().map(line -> Map.of(
                                "sku", line.sku(),
                                "productName", line.productName(),
                                "categoryName", line.categoryName(),
                                "quantity", line.quantity(),
                                "unitPrice", line.unitPrice(),
                                "missingQuantity", line.missingQuantity()))
                                .toList());
                        return sliceMap;
                    }).toList());
                    return map;
                })
                .toList();
    }

    private void addCategoryMetric(Map<String, CategoryMetric> metrics, CreatedLine line) {
        CategoryMetric current = metrics.get(line.categoryName());
        BigDecimal lineRevenue = line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
        if (current == null) {
            metrics.put(line.categoryName(), new CategoryMetric(line.categoryName(), lineRevenue, line.quantity()));
            return;
        }
        metrics.put(line.categoryName(), new CategoryMetric(
                line.categoryName(),
                current.revenue().add(lineRevenue),
                current.quantity() + line.quantity()));
    }

    private BigDecimal subtotal(List<OrderLinePlan> lines, CatalogSeed catalog) {
        return lines.stream()
                .map(line -> catalog.variantsByKey().get(line.variantKey()).plan().sellingPrice()
                        .multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal cost(List<OrderLinePlan> lines, CatalogSeed catalog) {
        return lines.stream()
                .map(line -> catalog.variantsByKey().get(line.variantKey()).plan().importPrice()
                        .multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int totalMissing(List<OrderLinePlan> lines) {
        return lines.stream().mapToInt(OrderLinePlan::missingQuantity).sum();
    }

    private List<CreatedLine> toCreatedLines(List<OrderLinePlan> lines, Branch branch, CatalogSeed catalog) {
        return lines.stream()
                .map(line -> {
                    DemoVariant variant = catalog.variantsByKey().get(line.variantKey());
                    return new CreatedLine(
                            line.variantKey(),
                            variant.variant().getSku(),
                            variant.product().getName(),
                            variant.product().getCategory().getName(),
                            branch.getId(),
                            branch.getName(),
                            line.quantity(),
                            line.allocatedQuantity(),
                            line.missingQuantity(),
                            variant.plan().sellingPrice(),
                            variant.plan().importPrice());
                })
                .toList();
    }

    private BigDecimal allocateDiscount(BigDecimal subtotal, BigDecimal orderSubtotal, BigDecimal orderDiscount) {
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0
                || orderSubtotal.compareTo(BigDecimal.ZERO) <= 0
                || orderDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return orderDiscount.multiply(subtotal).divide(orderSubtotal, 2, RoundingMode.HALF_UP);
    }

    private double growth(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private double growth(long current, long previous) {
        if (previous == 0) {
            return 0.0;
        }
        return ((double) (current - previous) / previous) * 100.0;
    }

    private boolean isNew(BigDecimal current, BigDecimal previous) {
        return (previous == null || previous.compareTo(BigDecimal.ZERO) == 0)
                && current != null
                && current.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isNew(long current, long previous) {
        return previous == 0 && current > 0;
    }

    private FulfillmentStatus fulfillmentStatus(OrderStatus status) {
        return switch (status) {
            case PROCESSING -> FulfillmentStatus.PREPARING;
            case READY_FOR_PICKUP -> FulfillmentStatus.READY_TO_SHIP;
            case SHIPPING -> FulfillmentStatus.SHIPPING;
            case RECEIVED, COMPLETED -> FulfillmentStatus.DELIVERED;
            case RETURNED -> FulfillmentStatus.RETURNED;
            default -> FulfillmentStatus.NOT_STARTED;
        };
    }

    private LocalDateTime receivedAt(OrderStatus status, LocalDateTime createdAt, LocalDateTime now) {
        if (status == OrderStatus.RECEIVED || status == OrderStatus.COMPLETED) {
            return capAtNow(createdAt.plusHours(6), now);
        }
        return null;
    }

    private LocalDateTime completedAt(OrderStatus status, LocalDateTime createdAt, LocalDateTime now) {
        if (status == OrderStatus.COMPLETED) {
            return capAtNow(createdAt.plusHours(10), now);
        }
        return null;
    }

    private LocalDateTime cancelledAt(OrderStatus status, LocalDateTime createdAt, LocalDateTime now) {
        if (status == OrderStatus.CANCELLED) {
            return capAtNow(createdAt.plusHours(1), now);
        }
        return null;
    }

    private LocalDateTime capAtNow(LocalDateTime candidate, LocalDateTime now) {
        return candidate.isAfter(now) ? now : candidate;
    }

    private LocalDateTime todayAt(LocalDateTime now, int hour, int minute) {
        LocalDateTime candidate = now.toLocalDate().atTime(hour, minute);
        if (candidate.isAfter(now.minusMinutes(1))) {
            LocalDateTime fallback = now.minusMinutes(1);
            return fallback.isBefore(now.toLocalDate().atStartOfDay()) ? now : fallback;
        }
        return candidate;
    }

    private LocalDateTime currentMonthPastAt(LocalDateTime now, int preferredDaysBeforeToday, int hour, int minute) {
        long availablePastDaysInMonth = now.getDayOfMonth() - 1L;
        long daysBeforeToday = Math.min(preferredDaysBeforeToday, availablePastDaysInMonth);
        LocalDate date = now.toLocalDate().minusDays(daysBeforeToday);
        if (date.equals(now.toLocalDate())) {
            return todayAt(now, hour, minute);
        }
        return atDay(date, hour, minute);
    }

    private LocalDateTime atDay(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute);
    }

    private OrderLinePlan line(String variantKey, int quantity) {
        return new OrderLinePlan(variantKey, quantity, quantity, 0);
    }

    private OrderLinePlan lineMissing(String variantKey, int quantity, int allocatedQuantity, int missingQuantity) {
        return new OrderLinePlan(variantKey, quantity, allocatedQuantity, missingQuantity);
    }

    private SubOrderPlan slice(Branch branch, OrderStatus status, BigDecimal shippingFee, List<OrderLinePlan> lines) {
        return new SubOrderPlan(branch, status, shippingFee, lines);
    }

    private String inventoryKey(Branch branch, String variantKey) {
        return branch.getId() + ":" + variantKey;
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void forceTimestamp(String tableName, Long id, LocalDateTime timestamp) {
        entityManager.createNativeQuery("UPDATE " + tableName + " SET created_at = :createdAt, updated_at = :updatedAt WHERE id = :id")
                .setParameter("createdAt", timestamp)
                .setParameter("updatedAt", timestamp)
                .setParameter("id", id)
                .executeUpdate();
    }

    private record BranchPlan(
            String code,
            String name,
            String phone,
            String email,
            String address,
            Double lat,
            Double lng) {
    }

    private record BranchSeed(List<Branch> branches, int createdCount) {
    }

    private record VariantPlan(
            String key,
            String categoryName,
            String productName,
            String slug,
            String baseSku,
            String sku,
            String variantName,
            BigDecimal sellingPrice,
            BigDecimal importPrice,
            int branchOneQty,
            int branchTwoQty) {
    }

    private record DemoVariant(VariantPlan plan, Product product, ProductVariant variant) {
    }

    private record CatalogSeed(
            List<Category> categories,
            List<Product> products,
            List<ProductVariant> variants,
            Map<String, DemoVariant> variantsByKey,
            Map<String, Inventory> inventoriesByKey) {
    }

    private record OrderLinePlan(String variantKey, int quantity, int allocatedQuantity, int missingQuantity) {
    }

    private record SubOrderPlan(Branch branch, OrderStatus status, BigDecimal shippingFee, List<OrderLinePlan> lines) {
    }

    private record CreatedLine(
            String variantKey,
            String sku,
            String productName,
            String categoryName,
            Long branchId,
            String branchName,
            int quantity,
            int allocatedQuantity,
            int missingQuantity,
            BigDecimal unitPrice,
            BigDecimal importPrice) {
    }

    private record CreatedSlice(
            Long branchId,
            String branchName,
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal allocatedDiscount,
            BigDecimal netRevenue,
            BigDecimal cost,
            int missingQuantity,
            List<CreatedLine> lines) {
    }

    private record CreatedOrder(
            String code,
            OrderStatus status,
            LocalDateTime createdAt,
            boolean legacy,
            Long orderBranchId,
            String orderBranchName,
            BigDecimal totalAmount,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal netRevenue,
            BigDecimal cost,
            List<CreatedSlice> slices,
            List<CreatedLine> parentLines,
            long saleTransactionCount) {
    }

    private record WarehouseSeed(List<InventoryNote> inventoryNotes, List<InventoryTransfer> transfers) {
    }

    private record ScopeMetrics(
            Map<String, Object> stats,
            Map<String, Object> customerInsights,
            Map<String, Object> dailyResults,
            Map<String, Object> monthlyResults,
            Map<String, Object> pendingOrdersSummary,
            Map<String, Object> inventoryInfo,
            Map<String, Object> backorders) {

        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("stats", stats);
            map.put("customerInsights", customerInsights);
            map.put("dailyResults", dailyResults);
            map.put("monthlyResults", monthlyResults);
            map.put("pendingOrdersSummary", pendingOrdersSummary);
            map.put("inventoryInfo", inventoryInfo);
            map.put("backorders", backorders);
            return map;
        }
    }

    private record Metrics(
            Map<String, Object> expectedDashboardNumbers,
            List<Map<String, Object>> branchSummaries,
            List<Map<String, Object>> salesPerformance7Days,
            List<Map<String, Object>> categoryDistribution,
            List<Map<String, Object>> topProducts) {
    }

    private record CategoryMetric(String categoryName, BigDecimal revenue, long quantity) {
    }

    private record ProductMetric(String productName, long quantity, BigDecimal revenue) {
    }
}

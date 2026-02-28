package com.zone.agri.config;

import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.*;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final SKUAttributeValueRepository skuAttributeValueRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryNoteDetailRepository inventoryNoteDetailRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryTransferRepository inventoryTransferRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (roleRepository.count() > 0) {
            log.info(">>> Dữ liệu đã được khởi tạo. Bỏ qua...");
            return;
        }
        log.info(">>> BẮT ĐẦU KHỞI TẠO DỮ LIỆU DEMO (MÔ HÌNH LÔ HÀNG ĐỘNG)...");

        // ── 1. CHI NHÁNH & KHO ──────────────────────────────────────────
        Branch mainWh  = saveBranch("MAIN_WH",   "WAREHOUSE", "Kho Tổng Cần Thơ",    "02921112222", "khotong@agrishrimp.vn", "99 Nguyễn Văn Cừ, P.An Khánh, Q.Ninh Kiều, Cần Thơ", 10.0341, 105.7904, 92, 916);
        Branch branch1 = saveBranch("BRANCH_01", "STORE",     "Chi Nhánh Cần Thơ",   "02923334444", "cn1@agrishrimp.vn",     "15 Mậu Thân, P.Xuân Khánh, Q.Ninh Kiều, Cần Thơ", 10.0300, 105.7700, 92, 916);
        Branch branch2 = saveBranch("BRANCH_02", "STORE",     "Chi Nhánh Sóc Trăng", "02995556666", "cn2@agrishrimp.vn",     "21 Trần Hưng Đạo, P.1, TP.Sóc Trăng, Sóc Trăng", 9.6025, 105.9731, 94, 941);

        // ── 2. PHÂN QUYỀN ───────────────────────────────────────────────

        // ============== 1 GROUP SYSTEM ==============
        Permission mDash  = pMod("Tổng quan hệ thống", "DASHBOARD", PermissionGroup.SYSTEM);
        Permission aDashV = pAct("Xem tổng quan", "DASHBOARD_VIEW", PermissionGroup.SYSTEM, mDash);

        Permission mWspace  = pMod("Bàn làm việc", "WORKSPACE", PermissionGroup.SYSTEM);
        Permission aWspaceV = pAct("Xem bàn làm việc", "WORKSPACE_VIEW", PermissionGroup.SYSTEM, mWspace);


        // ============== 2 GROUP REPORT ==============
        Permission mRpt     = pMod("Báo cáo", "REPORT", PermissionGroup.REPORT);
        Permission aRptSale = pAct("Báo cáo doanh thu", "REPORT_REVENUE_VIEW", PermissionGroup.REPORT, mRpt);
        Permission aRptInv  = pAct("Báo cáo kho", "REPORT_INVENTORY_VIEW", PermissionGroup.REPORT, mRpt);
        Permission aRptFin  = pAct("Báo cáo tài chính", "REPORT_FINANCE_VIEW", PermissionGroup.REPORT, mRpt);


        // ============== 3 GROUP ADMINISTRATION ==============
        Permission mUser  = pMod("Quản lý nhân viên", "STAFF", PermissionGroup.ADMINISTRATION);
        Permission aUserV = pAct("Xem nhân viên", "STAFF_VIEW", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserC = pAct("Thêm nhân viên", "STAFF_CREATE", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserU = pAct("Sửa nhân viên", "STAFF_UPDATE", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserD = pAct("Xóa nhân viên", "STAFF_DELETE", PermissionGroup.ADMINISTRATION, mUser);

        Permission mBranch  = pMod("Quản lý chi nhánh", "BRANCH", PermissionGroup.ADMINISTRATION);
        Permission aBranchV = pAct("Xem chi nhánh", "BRANCH_VIEW", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchC = pAct("Thêm chi nhánh", "BRANCH_CREATE", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchU = pAct("Sửa chi nhánh", "BRANCH_UPDATE", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchD = pAct("Xóa chi nhánh", "BRANCH_DELETE", PermissionGroup.ADMINISTRATION, mBranch);

        Permission mRole  = pMod("Quản lý vai trò", "ROLE", PermissionGroup.ADMINISTRATION);
        Permission aRoleV = pAct("Xem vai trò", "ROLE_VIEW", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleC = pAct("Tạo vai trò", "ROLE_CREATE", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleU = pAct("Sửa vai trò", "ROLE_UPDATE", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleD = pAct("Xóa vai trò", "ROLE_DELETE", PermissionGroup.ADMINISTRATION, mRole);


        // ============== 4 GROUP SALES ==============
        Permission mOrder     = pMod("Quản lý đơn hàng", "ORDER", PermissionGroup.SALES);
        Permission aOrdV      = pAct("Xem đơn hàng", "ORDER_VIEW", PermissionGroup.SALES, mOrder);
        Permission aOrdC      = pAct("Tạo đơn hàng", "ORDER_CREATE", PermissionGroup.SALES, mOrder);
        Permission aOrdU      = pAct("Cập nhật đơn hàng", "ORDER_UPDATE", PermissionGroup.SALES, mOrder);
        Permission aOrdCnf    = pAct("Xác nhận đơn hàng", "ORDER_CONFIRM", PermissionGroup.SALES, mOrder);
        Permission aOrdShip   = pAct("Giao hàng", "ORDER_SHIP", PermissionGroup.SALES, mOrder);
        Permission aOrdX      = pAct("Huỷ đơn hàng", "ORDER_CANCEL", PermissionGroup.SALES, mOrder);
        Permission aOrdDone   = pAct("Hoàn tất đơn hàng", "ORDER_COMPLETE", PermissionGroup.SALES, mOrder);
        Permission aOrdExport = pAct("Xuất danh sách đơn hàng", "ORDER_EXPORT", PermissionGroup.SALES, mOrder);
        Permission aOrdRefund = pAct("Hoàn tiền đơn hàng", "ORDER_REFUND", PermissionGroup.SALES, mOrder);
        Permission aOrdD      = pAct("Xóa đơn hàng", "ORDER_DELETE", PermissionGroup.SALES, mOrder);

        Permission mCus  = pMod("Quản lý khách hàng", "CUSTOMER", PermissionGroup.SALES);
        Permission aCusV = pAct("Xem khách hàng", "CUSTOMER_VIEW", PermissionGroup.SALES, mCus);
        Permission aCusC = pAct("Thêm khách hàng", "CUSTOMER_CREATE", PermissionGroup.SALES, mCus);
        Permission aCusU = pAct("Sửa khách hàng", "CUSTOMER_UPDATE", PermissionGroup.SALES, mCus);
        Permission aCusD = pAct("Xóa khách hàng", "CUSTOMER_DELETE", PermissionGroup.SALES, mCus);


        // ============== 5 GROUP PRODUCT_CATALOG ==============
        Permission mProd  = pMod("Quản lý sản phẩm", "PRODUCT", PermissionGroup.PRODUCT_CATALOG);
        Permission aProdV = pAct("Xem sản phẩm", "PRODUCT_VIEW", PermissionGroup.PRODUCT_CATALOG, mProd);
        Permission aProdC = pAct("Thêm sản phẩm", "PRODUCT_CREATE", PermissionGroup.PRODUCT_CATALOG, mProd);
        Permission aProdU = pAct("Sửa sản phẩm", "PRODUCT_UPDATE", PermissionGroup.PRODUCT_CATALOG, mProd);
        Permission aProdD = pAct("Xóa sản phẩm", "PRODUCT_DELETE", PermissionGroup.PRODUCT_CATALOG, mProd);

        Permission mCat  = pMod("Quản lý danh mục", "CATEGORY", PermissionGroup.PRODUCT_CATALOG);
        Permission aCatV = pAct("Xem danh mục", "CATEGORY_VIEW", PermissionGroup.PRODUCT_CATALOG, mCat);
        Permission aCatC = pAct("Thêm danh mục", "CATEGORY_CREATE", PermissionGroup.PRODUCT_CATALOG, mCat);
        Permission aCatU = pAct("Sửa danh mục", "CATEGORY_UPDATE", PermissionGroup.PRODUCT_CATALOG, mCat);
        Permission aCatD = pAct("Xóa danh mục", "CATEGORY_DELETE", PermissionGroup.PRODUCT_CATALOG, mCat);

        Permission mAttr  = pMod("Quản lý thuộc tính", "ATTRIBUTE", PermissionGroup.PRODUCT_CATALOG);
        Permission aAttrV = pAct("Xem thuộc tính", "ATTRIBUTE_VIEW", PermissionGroup.PRODUCT_CATALOG, mAttr);
        Permission aAttrC = pAct("Thêm thuộc tính", "ATTRIBUTE_CREATE", PermissionGroup.PRODUCT_CATALOG, mAttr);
        Permission aAttrU = pAct("Sửa thuộc tính", "ATTRIBUTE_UPDATE", PermissionGroup.PRODUCT_CATALOG, mAttr);
        Permission aAttrD = pAct("Xóa thuộc tính", "ATTRIBUTE_DELETE", PermissionGroup.PRODUCT_CATALOG, mAttr);


        // ============== 6 GROUP INVENTORY ==============
        Permission mSup  = pMod("Quản lý nhà cung cấp", "SUPPLIER", PermissionGroup.INVENTORY);
        Permission aSupV = pAct("Xem nhà cung cấp", "SUPPLIER_VIEW", PermissionGroup.INVENTORY, mSup);
        Permission aSupC = pAct("Thêm nhà cung cấp", "SUPPLIER_CREATE", PermissionGroup.INVENTORY, mSup);
        Permission aSupU = pAct("Sửa nhà cung cấp", "SUPPLIER_UPDATE", PermissionGroup.INVENTORY, mSup);
        Permission aSupD = pAct("Xóa nhà cung cấp", "SUPPLIER_DELETE", PermissionGroup.INVENTORY, mSup);

        Permission mImp  = pMod("Quản lý nhập hàng", "IMPORT", PermissionGroup.INVENTORY);
        Permission aImpV = pAct("Xem phiếu nhập", "IMPORT_VIEW", PermissionGroup.INVENTORY, mImp);
        Permission aImpC = pAct("Tạo phiếu nhập", "IMPORT_CREATE", PermissionGroup.INVENTORY, mImp);
        Permission aImpU = pAct("Sửa phiếu nhập", "IMPORT_UPDATE", PermissionGroup.INVENTORY, mImp);
        Permission aImpA = pAct("Duyệt phiếu nhập", "IMPORT_APPROVE", PermissionGroup.INVENTORY, mImp);
        Permission aImpX = pAct("Hủy phiếu nhập", "IMPORT_CANCEL", PermissionGroup.INVENTORY, mImp);
        Permission aImpD = pAct("Xóa phiếu nhập", "IMPORT_DELETE", PermissionGroup.INVENTORY, mImp);

        Permission mExp  = pMod("Quản lý xuất hàng", "EXPORT", PermissionGroup.INVENTORY);
        Permission aExpV = pAct("Xem phiếu xuất", "EXPORT_VIEW", PermissionGroup.INVENTORY, mExp);
        Permission aExpC = pAct("Tạo phiếu xuất", "EXPORT_CREATE", PermissionGroup.INVENTORY, mExp);
        Permission aExpA = pAct("Duyệt phiếu xuất", "EXPORT_APPROVE", PermissionGroup.INVENTORY, mExp);
        Permission aExpU = pAct("Sửa phiếu xuất", "EXPORT_UPDATE", PermissionGroup.INVENTORY, mExp);
        Permission aExpX = pAct("Hủy phiếu xuất", "EXPORT_CANCEL", PermissionGroup.INVENTORY, mExp);
        Permission aExpD = pAct("Xóa phiếu xuất", "EXPORT_DELETE", PermissionGroup.INVENTORY, mExp);

        Permission mTrf  = pMod("Quản lý điều chuyển", "TRANSFER", PermissionGroup.INVENTORY);
        Permission aTrfV = pAct("Xem phiếu điều chuyển", "TRANSFER_VIEW", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfC = pAct("Tạo phiếu điều chuyển", "TRANSFER_CREATE", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfA = pAct("Duyệt điều chuyển", "TRANSFER_APPROVE", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfX = pAct("Hủy điều chuyển", "TRANSFER_CANCEL", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfD = pAct("Xóa điều chuyển", "TRANSFER_DELETE", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfU = pAct("Sửa điều chuyển", "TRANSFER_UPDATE", PermissionGroup.INVENTORY, mTrf);


        // ============== 7 GROUP SETTING ==============
        Permission mSet  = pMod("Cài đặt hệ thống", "SETTING", PermissionGroup.SETTING);
        Permission aSetV = pAct("Xem cài đặt", "SETTING_VIEW", PermissionGroup.SETTING, mSet);
        Permission aSetU = pAct("Cập nhật cài đặt", "SETTING_UPDATE", PermissionGroup.SETTING, mSet);


        // ── 3. VAI TRÒ ──────────────────────────────────────────────────
        Role adminRole = saveRole("ADMIN", "Quản trị viên", false, Set.of(
                aDashV, aWspaceV,
                aRptSale, aRptInv, aRptFin,
                aUserV, aUserC, aUserU, aUserD,
                aRoleV, aRoleC, aRoleU, aRoleD,
                aBranchV, aBranchC, aBranchU, aBranchD,
                aProdV, aProdC, aProdU, aProdD,
                aCatV, aCatC, aCatU, aCatD,
                aAttrV, aAttrC, aAttrU, aAttrD,
                aImpV, aImpC, aImpA, aImpU, aImpX, aImpD,
                aExpV, aExpC, aExpA, aExpU, aExpX, aExpD,
                aTrfV, aTrfC, aTrfA, aTrfU, aTrfX, aTrfD,
                aCusV, aCusC, aCusU, aCusD,
                aSupV, aSupC, aSupU, aSupD,
                aOrdV, aOrdC, aOrdU, aOrdD, aOrdCnf, aOrdShip, aOrdX, aOrdDone, aOrdRefund, aOrdExport
        ));

        Role managerRole = saveRole("MANAGER", "Quản lý chi nhánh & kho", false, Set.of(
                aDashV, aWspaceV,
                aRptSale, aRptInv, aRptFin,
                aUserV, aUserC, aUserU, aUserD,
                aBranchV,
                aProdV,
                aCatV,
                aAttrV,
                aSupV,
                aImpV, aImpC, aImpU, aImpX, aImpD,
                aExpV, aExpC, aExpU, aExpX, aExpD,
                aTrfV, aTrfC, aTrfU, aTrfX, aTrfD,
                aCusV, aCusC, aCusU, aCusD,
                aOrdV, aOrdC, aOrdU, aOrdCnf, aOrdShip, aOrdX, aOrdDone, aOrdExport
        ));

        Role staffRole = saveRole("STAFF", "Nhân viên", false, Set.of(
                aDashV, aWspaceV,
                aRptInv,
                aProdV,
                aCatV,
                aOrdV, aOrdC
        ));

        Role userRole = saveRole("USER", "Khách hàng", false, Set.of(aOrdV, aOrdC, aOrdX));


        // ── 4. NGƯỜI DÙNG ────────────────────────────────────────────────
        User admin  = saveUser("Nguyễn Văn Admin",  "admin@agrishrimp.vn",  "0901000001", "123456", adminRole,    mainWh,  Gender.MALE,   LocalDate.of(1985,  3, 15));
        User kho1   = saveUser("Trần Thị Kho Một",  "kho1@agrishrimp.vn",   "0901000002", "123456", managerRole,  mainWh,  Gender.FEMALE, LocalDate.of(1992,  7, 20));
        User kho2   = saveUser("Lê Văn Kho Hai",    "kho2@agrishrimp.vn",   "0901000003", "123456", managerRole,  branch1, Gender.MALE,   LocalDate.of(1993, 11,  5));
        User user1  = saveUser("Nguyễn Văn Tôm",    "user1@gmail.com",      "0911000001", "123456", userRole,     null,    Gender.MALE,   LocalDate.of(1988,  6, 12));
        User user2  = saveUser("Trần Thị Cua",      "user2@gmail.com",      "0911000002", "123456", userRole,     null,    Gender.FEMALE, LocalDate.of(1990,  9, 25));
        User user3  = saveUser("Lê Minh Nuôi",      "user3@gmail.com",      "0911000003", "123456", userRole,     null,    Gender.MALE,   LocalDate.of(1995,  2, 18));

        // ── 5. DANH MỤC  ──────────────────────────
        Category catFeed   = categoryRepository.save(cat("Thức Ăn Thủy Sản",         null));
        Category catChem   = categoryRepository.save(cat("Thuốc & Chế Phẩm Sinh Học", null));
        Category catMine   = categoryRepository.save(cat("Khoáng Chất & Dinh Dưỡng",  null));
        Category catEquip  = categoryRepository.save(cat("Dụng Cụ & Trang Thiết Bị",  null));

        Category catSF     = categoryRepository.save(cat("Thức Ăn Tôm",               catFeed));
        Category catFF     = categoryRepository.save(cat("Thức Ăn Cá",                catFeed));
        Category catPro    = categoryRepository.save(cat("Chế Phẩm Vi Sinh",           catChem));
        Category catMed    = categoryRepository.save(cat("Thuốc Phòng Trị Bệnh",      catChem));
        Category catMin    = categoryRepository.save(cat("Khoáng Chất",               catMine));
        Category catVit    = categoryRepository.save(cat("Vitamin & Enzyme",           catMine));
        Category catMeas   = categoryRepository.save(cat("Dụng Cụ Đo Lường",          catEquip));
        Category catPondE  = categoryRepository.save(cat("Thiết Bị Ao Nuôi",          catEquip));

        // ── 6. THƯƠNG HIỆU ───────────────────────────────────────────────
        Brand bCP      = brandRepository.save(Brand.builder().name("CP Vietnam").status(BrandStatus.ACTIVE).build());
        Brand bTomboy  = brandRepository.save(Brand.builder().name("Tomboy Feed").status(BrandStatus.ACTIVE).build());
        Brand bGN      = brandRepository.save(Brand.builder().name("Green Nature").status(BrandStatus.ACTIVE).build());

        // ── 7. THUỘC TÍNH & GIÁ TRỊ ──────────────────────────────────────
        Attribute attrW = attributeRepository.save(Attribute.builder()
                .name("Trọng Lượng")
                .code("TRONG_LUONG")
                .status(AttributeStatus.ACTIVE)
                .build());

        Attribute attrP = attributeRepository.save(Attribute.builder()
                .name("Quy Cách Đóng Gói")
                .code("QUY_CACH_DONG_GOI")
                .status(AttributeStatus.ACTIVE)
                .build());

        // Tạo và lưu các giá trị chi tiết vào bảng attribute_values thông qua hàm av()
        AttributeValue av500g  = av(attrW, "500g");
        AttributeValue av5kg   = av(attrW, "5kg");
        AttributeValue av25kg  = av(attrW, "25kg");

        AttributeValue avBag    = av(attrP, "Túi PE");
        AttributeValue avSack   = av(attrP, "Bao PP");
        AttributeValue avBottle = av(attrP, "Chai nhựa");

        // ── 8. SẢN PHẨM & BIẾN THỂ (LƯU Ý: KHÔNG CÒN SET GIÁ) ────────────
        Product p1  = prod("Thức Ăn Tôm CP 8012", "thuc-an-tom-cp-8012",
                "Thức ăn viên chìm cho tôm thẻ", "Thức ăn tôm CP 8012...", bCP, catSF, "Việt Nam", "CP8012");
        ProductVariant pv1a = pv(p1, "CP8012-5KG",  "8935001001011", VariantStatus.ACTIVE);
        ProductVariant pv1b = pv(p1, "CP8012-25KG", "8935001001012", VariantStatus.ACTIVE);
        skua(pv1a, attrW, av5kg);   skua(pv1a, attrP, avBag);
        skua(pv1b, attrW, av25kg);  skua(pv1b, attrP, avSack);

        Product p4  = prod("Chế Phẩm EM Xử Lý Ao Nuôi", "che-pham-em",
                "Vi khuẩn có lợi làm sạch đáy ao", "Chế phẩm EM chứa Bacillus...", bGN, catPro, "Việt Nam", "EM-XL");
        ProductVariant pv4a = pv(p4, "EM-XL-500ML", "8935004001011", VariantStatus.ACTIVE);
        skua(pv4a, attrW, av500g);  skua(pv4a, attrP, avBottle);

        // ── 9. NHÀ CUNG CẤP ─────────────────────────────────────────────
        Supplier supCP  = supplierRepository.save(Supplier.builder()
                .code("NCC-001").name("Công Ty TNHH CP Vietnam").taxCode("0101234567")
                .contactName("Nguyễn Văn Hùng").phone("0901999101").email("supply@cpvietnam.com.vn")
                .provinceId("79").addressDetail("KCN Mỹ Phước, Bình Dương").status(SupplierStatus.ACTIVE).build());

        Supplier supGN  = supplierRepository.save(Supplier.builder()
                .code("NCC-002").name("Công Ty TNHH Green Nature Việt Nam").taxCode("0201234567")
                .contactName("Trần Thị Lan").phone("0902999202").email("sales@greennature.vn")
                .provinceId("79").addressDetail("Lô B12, KCN Trà Nóc 2, Cần Thơ").status(SupplierStatus.ACTIVE).build());

        // ── 10. TỒN KHO THEO LÔ (GÁN GIÁ VỐN + BATCH VÀO INVENTORY) ──────
        LocalDateTime now = LocalDateTime.now();
        // Lô hàng 1: Nhập tháng trước, giá rẻ hơn
        inv(pv1a, mainWh, 50, bd(140_000), "LOT-CP-OLD", 10, now);
        inv(pv1b, mainWh, 20, bd(660_000), "LOT-CP-OLD", 5, now);

        // Lô hàng 2: Nhập mới, giá cao hơn
        inv(pv1a, mainWh, 100, bd(145_000), "LOT-CP-NEW", 10, now);

        inv(pv1a, branch1, 30, bd(140_000), "LOT-CP-OLD", 5, now);
        inv(pv4a, branch1, 50, bd(62_000), "LOT-EM-01", 10, now);

        inv(pv1a, branch2, 20, bd(142_000), "LOT-CP-MID", 5, now);

        // ── 11. KHÁCH HÀNG ──────────────────────────────────────────────
        customerRepository.save(Customer.builder().user(user1).name("Nguyễn Văn Tôm").phone("0911000001").email("user1@gmail.com").gender(CustomerGender.MALE).provinceId("79").addressDetail("Cà Mau").status(CustomerStatus.ACTIVE).build());
        customerRepository.save(Customer.builder().user(user2).name("Trần Thị Cua").phone("0911000002").email("user2@gmail.com").gender(CustomerGender.FEMALE).provinceId("94").addressDetail("Sóc Trăng").status(CustomerStatus.ACTIVE).build());

        // ── 12. ĐƠN HÀNG ────────────────────────────────────────────────
        Order o1 = orderRepository.save(Order.builder()
                .code("DH-2024-001").branch(branch1).user(user1).status(OrderStatus.COMPLETED)
                .paymentMethod(PaymentMethod.TRANSFER).paymentStatus(PaymentStatus.PAID)
                .shippingAddress("Cà Mau").totalAmount(bd(420_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(420_000))
                .createdAt(now.minusDays(15)).build());

        // Tính giá bán giả lập cho OrderItem (Giá Vốn * 1.3) = 140,000 * 1.3 = 182,000
        orderItemRepository.save(oi(o1, pv1a, 3, bd(182_000)));

        log.info(">>> KHỞI TẠO DỮ LIỆU DEMO HOÀN TẤT.");
    }

    // ====================================================================
    // PRIVATE HELPERS
    // ====================================================================

    private Branch saveBranch(String code, String type, String name, String phone, String email, String address, Double lat, Double lng, Integer provinceId, Integer districtId) {
        return branchRepository.findByBranchCode(code).orElseGet(() ->
                branchRepository.save(Branch.builder()
                        .branchCode(code).branchType(type).name(name)
                        .phone(phone).email(email).addressDetail(address)
                        .lat(lat).lng(lng)
                        .provinceId(provinceId).districtId(districtId)
                        .status(BranchStatus.ACTIVE).build()));
    }

    private Permission pMod(String name, String code, PermissionGroup group) {
        return permissionRepository.findByCode(code).orElseGet(() ->
                permissionRepository.save(Permission.builder()
                        .name(name).code(code).groupName(group).type(PermissionType.MODULE).build()));
    }

    private Permission pAct(String name, String code, PermissionGroup group, Permission parent) {
        return permissionRepository.findByCode(code).orElseGet(() ->
                permissionRepository.save(Permission.builder()
                        .name(name).code(code).groupName(group)
                        .type(PermissionType.ACTION).parentId(parent.getId()).build()));
    }

    private Role saveRole(String slug, String displayName, boolean isSystem, Set<Permission> perms) {
        return roleRepository.findBySlug(slug).orElseGet(() ->
                roleRepository.save(Role.builder()
                        .slug(slug).displayName(displayName).isSystem(isSystem).isActive(true)
                        .description("Vai trò " + displayName).permissions(perms).build()));
    }

    private User saveUser(String name, String email, String phone, String pass,
                          Role role, Branch branch, Gender gender, LocalDate dob) {
        if (userRepository.existsByEmail(email)) return null;
        return userRepository.save(User.builder()
                .fullName(name).email(email).phoneNumber(phone)
                .passwordHash(passwordEncoder.encode(pass))
                .status(UserStatus.ACTIVE).role(role).branch(branch)
                .gender(gender).dateOfBirth(dob).provider(AuthProvider.LOCAL).build());
    }

    private Category cat(String name, Category parent) {
        return Category.builder()
                .name(name)
                .parent(parent)
                .status(CategoryStatus.ACTIVE)
                .build();
    }

    private AttributeValue av(Attribute attr, String value) {
        return attributeValueRepository.save(
                AttributeValue.builder().attribute(attr).value(value).build());
    }

    private Product prod(String name, String slug, String shortDesc, String desc,
                         Brand brand, Category cat, String origin, String baseSku) {
        return productRepository.findBySlug(slug).orElseGet(() ->
                productRepository.save(Product.builder()
                        .name(name).slug(slug).shortDesc(shortDesc).description(desc)
                        .brand(brand).category(cat).origin(origin).baseSku(baseSku)
                        .status(ProductStatus.ACTIVE).ratingAverage(4.5f).reviewCount(0)
                        .createdAt(LocalDateTime.now()).build()));
    }

    private ProductVariant pv(Product product, String sku, String barcode, VariantStatus status) {
        return productVariantRepository.findBySku(sku).orElseGet(() ->
                productVariantRepository.save(ProductVariant.builder()
                        .sku(sku).barcode(barcode).status(status).product(product).build()));
    }

    private void skua(ProductVariant variant, Attribute attr, AttributeValue attrVal) {
        skuAttributeValueRepository.save(
                SKUAttributeValue.builder().sku(variant).attribute(attr).attributeValue(attrVal).build());
    }

    // Thêm Giá Vốn và Mã Lô vào Inventory
    private void inv(ProductVariant variant, Branch branch, int qty, BigDecimal importPrice, String batchCode, int minStock, LocalDateTime now) {
        inventoryRepository.save(Inventory.builder()
                .productVariant(variant).branch(branch).quantity(qty)
                .importPrice(importPrice).batchNumber(batchCode)
                .minStock(minStock).lastCheckedAt(now).lastReceiptDate(now).build());
    }

    private OrderItem oi(Order order, ProductVariant variant, int qty, BigDecimal price) {
        return OrderItem.builder().order(order).productVariant(variant)
                .quantity(qty).price(price).build();
    }

    private BigDecimal bd(double val) {
        return BigDecimal.valueOf(val);
    }
}

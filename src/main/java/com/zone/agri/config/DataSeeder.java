package com.zone.agri.config;

import com.zone.agri.entity.*;
import com.zone.agri.entity.Order;
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

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;

@Component

@org.springframework.core.annotation.Order(1)

@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final Environment environment;

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
    private final ProductImageRepository productImageRepository;
    private final InventoryTransferRepository inventoryTransferRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        boolean hasExistingRoles = roleRepository.count() > 0;

        log.info(">>> ĐỒNG BỘ DỮ LIỆU HỆ THỐNG (permissions, roles, admin)...");

        // ── 1. PHÂN QUYỀN (Từ EssentialDataSeeder) ───────────────────────
        // GROUP SYSTEM
        Permission mDash    = pMod("Tổng quan hệ thống", "DASHBOARD", PermissionGroup.SYSTEM);
        Permission aDashV   = pAct("Xem tổng quan", "DASHBOARD_VIEW", PermissionGroup.SYSTEM, mDash);
        Permission mWspace  = pMod("Bàn làm việc", "WORKSPACE", PermissionGroup.SYSTEM);
        Permission aWspaceV = pAct("Xem bàn làm việc", "WORKSPACE_VIEW", PermissionGroup.SYSTEM, mWspace);

        // GROUP REPORT
        Permission mRpt     = pMod("Báo cáo", "REPORT", PermissionGroup.REPORT);
        Permission aRptSale = pAct("Báo cáo doanh thu", "REPORT_REVENUE_VIEW", PermissionGroup.REPORT, mRpt);
        Permission aRptInv  = pAct("Báo cáo kho", "REPORT_INVENTORY_VIEW", PermissionGroup.REPORT, mRpt);
        Permission aRptFin  = pAct("Báo cáo tài chính", "REPORT_FINANCE_VIEW", PermissionGroup.REPORT, mRpt);

        // GROUP ADMINISTRATION
        Permission mUser    = pMod("Quản lý nhân viên", "STAFF", PermissionGroup.ADMINISTRATION);
        Permission aUserV   = pAct("Xem nhân viên", "STAFF_VIEW", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserC   = pAct("Thêm nhân viên", "STAFF_CREATE", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserU   = pAct("Sửa nhân viên", "STAFF_UPDATE", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserD   = pAct("Xóa nhân viên", "STAFF_DELETE", PermissionGroup.ADMINISTRATION, mUser);

        Permission mBranch  = pMod("Quản lý chi nhánh", "BRANCH", PermissionGroup.ADMINISTRATION);
        Permission aBranchV = pAct("Xem chi nhánh", "BRANCH_VIEW", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchC = pAct("Thêm chi nhánh", "BRANCH_CREATE", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchU = pAct("Sửa chi nhánh", "BRANCH_UPDATE", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchD = pAct("Xóa chi nhánh", "BRANCH_DELETE", PermissionGroup.ADMINISTRATION, mBranch);

        Permission mRole    = pMod("Quản lý vai trò", "ROLE", PermissionGroup.ADMINISTRATION);
        Permission aRoleV   = pAct("Xem vai trò", "ROLE_VIEW", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleC   = pAct("Tạo vai trò", "ROLE_CREATE", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleU   = pAct("Sửa vai trò", "ROLE_UPDATE", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleD   = pAct("Xóa vai trò", "ROLE_DELETE", PermissionGroup.ADMINISTRATION, mRole);

        // GROUP SALES
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

        Permission mVou  = pMod("Quản lý mã giảm giá", "VOUCHER", PermissionGroup.SALES);
        Permission aVouV = pAct("Xem mã giảm giá", "VOUCHER_VIEW", PermissionGroup.SALES, mVou);
        Permission aVouC = pAct("Thêm mã giảm giá", "VOUCHER_CREATE", PermissionGroup.SALES, mVou);
        Permission aVouU = pAct("Sửa mã giảm giá", "VOUCHER_UPDATE", PermissionGroup.SALES, mVou);
        Permission aVouD = pAct("Xóa mã giảm giá", "VOUCHER_DELETE", PermissionGroup.SALES, mVou);

        // GROUP PRODUCT_CATALOG
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

        // GROUP INVENTORY
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

        Permission mChk  = pMod("Kiểm kê kho", "INVENTORY_CHECK", PermissionGroup.INVENTORY);
        Permission aChkV = pAct("Xem phiếu kiểm kê", "INVENTORY_CHECK_VIEW", PermissionGroup.INVENTORY, mChk);
        Permission aChkC = pAct("Tạo phiếu kiểm kê", "INVENTORY_CHECK_CREATE", PermissionGroup.INVENTORY, mChk);
        Permission aChkA = pAct("Duyệt phiếu kiểm kê", "INVENTORY_CHECK_APPROVE", PermissionGroup.INVENTORY, mChk);
        Permission aChkU = pAct("Sửa phiếu kiểm kê", "INVENTORY_CHECK_UPDATE", PermissionGroup.INVENTORY, mChk);
        Permission aChkX = pAct("Hủy phiếu kiểm kê", "INVENTORY_CHECK_CANCEL", PermissionGroup.INVENTORY, mChk);
        Permission aChkD = pAct("Xóa phiếu kiểm kê", "INVENTORY_CHECK_DELETE", PermissionGroup.INVENTORY, mChk);

        // GROUP SETTING
        Permission mSet  = pMod("Cài đặt hệ thống", "SETTING", PermissionGroup.SETTING);
        Permission aSetV = pAct("Xem cài đặt", "SETTING_VIEW", PermissionGroup.SETTING, mSet);
        Permission aSetU = pAct("Cập nhật cài đặt", "SETTING_UPDATE", PermissionGroup.SETTING, mSet);

        // ── 2. VAI TRÒ ──────────────────────────────────────────────────
        Role adminRole = saveRole("ADMIN", "Quản trị viên", true, Set.of(
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
                aChkV, aChkC, aChkA, aChkU, aChkX, aChkD,
                aCusV, aCusC, aCusU, aCusD,
                aVouV, aVouC, aVouU, aVouD,
                aSupV, aSupC, aSupU, aSupD,
                aOrdV, aOrdC, aOrdU, aOrdD, aOrdCnf, aOrdShip, aOrdX, aOrdDone, aOrdRefund, aOrdExport,
                aSetV, aSetU
        ));

        saveRole("MANAGER", "Quản lý chi nhánh & kho", false, Set.of(
                aDashV, aWspaceV,
                aRptSale, aRptInv, aRptFin,
                aUserV, aUserC, aUserU, aUserD,
                aBranchV,
                aProdV, aCatV, aAttrV, aSupV,
                aImpV, aImpC, aImpU, aImpX, aImpD,
                aExpV, aExpC, aExpU, aExpX, aExpD,
                aTrfV, aTrfC, aTrfU, aTrfX, aTrfD,
                aChkV, aChkC, aChkU, aChkX, aChkD,
                aCusV, aCusC, aCusU, aCusD,
                aVouV, aVouC, aVouU,
                aOrdV, aOrdC, aOrdU, aOrdCnf, aOrdShip, aOrdX, aOrdDone, aOrdExport
        ));

        saveRole("STAFF", "Nhân viên", false, Set.of(
                aDashV, aWspaceV, aRptInv, aProdV, aCatV, aOrdV, aOrdC
        ));

        saveRole("USER", "Khách hàng", false, Set.of(aOrdV, aOrdC, aOrdX));

        // ── 3. ADMIN USER ────────────────────────────────────────────────
        if (!userRepository.existsByEmail("admin@agrishrimp.vn")) {
            userRepository.save(User.builder()
                    .fullName("Admin")
                    .email("admin@agrishrimp.vn")
                    .phoneNumber("0901000001")
                    .passwordHash(passwordEncoder.encode("123456"))
                    .status(UserStatus.ACTIVE)
                    .role(adminRole)
                    .gender(Gender.MALE)
                    .dateOfBirth(LocalDate.of(1985, 3, 15))
                    .provider(AuthProvider.LOCAL)
                    .build());
        }

        if (hasExistingRoles) {
            log.info(">>> ĐỒNG BỘ DỮ LIỆU HỆ THỐNG HOÀN TẤT.");
        } else {
            log.info(">>> KHỞI TẠO DỮ LIỆU HỆ THỐNG HOÀN TẤT.");
        }

        // Chỉ seed demo data khi chạy ở môi trường dev
        if (isDevProfile) {
            seedDemoData();
        } else {
            log.info(">>> Bỏ qua dữ liệu demo (profile: prod).");
        }
    }

    private void seedDemoData() {
        if (branchRepository.count() > 0) {
            log.info(">>> Dữ liệu demo đã tồn tại. Bỏ qua...");
            return;
        }
        log.info(">>> BẮT ĐẦU KHỞI TẠO DỮ LIỆU DEMO...");

        Role managerRole = roleRepository.findBySlug("MANAGER").orElseThrow();
        Role staffRole   = roleRepository.findBySlug("STAFF").orElseThrow();
        Role userRole    = roleRepository.findBySlug("USER").orElseThrow();

        // ── 0. NHÀ CUNG CẤP (Move up to use in Product seeding) ──────────
        Supplier supCP  = supplierRepository.save(Supplier.builder()
                .code("NCC-001").name("Công Ty TNHH CP Vietnam").taxCode("0101234567")
                .contactName("Nguyễn Văn Hùng").phone("0901999101").email("supply@cpvietnam.com.vn")
                .provinceId("79").addressDetail("KCN Mỹ Phước, Bình Dương").status(SupplierStatus.ACTIVE).build());

        Supplier supGN  = supplierRepository.save(Supplier.builder()
                .code("NCC-002").name("Công Ty TNHH Green Nature Việt Nam").taxCode("0201234567")
                .contactName("Trần Thị Lan").phone("0902999202").email("sales@greennature.vn")
                .provinceId("79").addressDetail("Lô B12, KCN Trà Nóc 2, Cần Thơ").status(SupplierStatus.ACTIVE).build());

        Supplier supTom = supplierRepository.save(Supplier.builder()
                .code("NCC-003").name("Công Ty TNHH Tomboy Feed Việt Nam").taxCode("0301234567")
                .contactName("Phạm Minh Tuấn").phone("0903999303").email("sales@tomboyfeed.vn")
                .provinceId("79").addressDetail("KCN Tân Tạo, Q.Bình Tân, TP.HCM").status(SupplierStatus.ACTIVE).build());

        Supplier supMW  = supplierRepository.save(Supplier.builder()
                .code("NCC-004").name("HTX Nông Nghiệp Miền Tây Xanh").taxCode("0401234567")
                .contactName("Võ Thị Hoa").phone("0904999404").email("supply@htxmientay.vn")
                .provinceId("92").addressDetail("KCN Trà Nóc 1, Q.Bình Thủy, TP.Cần Thơ").status(SupplierStatus.ACTIVE).build());

        // ── 1. CHI NHÁNH & KHO ──────────────────────────────────────────
        Branch mainWh  = saveBranch("MAIN_WH",   "WAREHOUSE", "Kho Tổng Cần Thơ",    "02921112222", "khotong@agrishrimp.vn", "99 Nguyễn Văn Cừ, P.An Khánh, Q.Ninh Kiều, Cần Thơ", 10.0341, 105.7904, 92, 916);
        Branch branch1 = saveBranch("BRANCH_01", "STORE",     "Chi Nhánh Cần Thơ",   "02923334444", "cn1@agrishrimp.vn",     "15 Mậu Thân, P.Xuân Khánh, Q.Ninh Kiều, Cần Thơ", 10.0300, 105.7700, 92, 916);
        Branch branch2  = saveBranch("BRANCH_02", "STORE", "Chi Nhánh Sóc Trăng",  "02995556666", "cn2@agrishrimp.vn",  "21 Trần Hưng Đạo, P.1, TP.Sóc Trăng, Sóc Trăng",        9.6025,  105.9731, 94, 941);
        Branch branch3  = saveBranch("BRANCH_03", "STORE", "Chi Nhánh Bạc Liêu",   "02913330001", "cn3@agrishrimp.vn",  "12 Ngô Đức Kế, P.7, TP.Bạc Liêu",                        9.2940,  105.7216, 95, 950);
        Branch branch4  = saveBranch("BRANCH_04", "STORE", "Chi Nhánh Cà Mau",     "02903330002", "cn4@agrishrimp.vn",  "45 Phan Ngọc Hiển, P.1, TP.Cà Mau",                      9.1769,  105.1500, 96, 964);
        Branch branch5  = saveBranch("BRANCH_05", "STORE", "Chi Nhánh Kiên Giang", "02973330003", "cn5@agrishrimp.vn",  "30 Lê Lợi, P.Vĩnh Thanh, TP.Rạch Giá",                  10.0129, 105.0802, 91, 910);
        Branch branch6  = saveBranch("BRANCH_06", "STORE", "Chi Nhánh An Giang",   "02963330004", "cn6@agrishrimp.vn",  "25 Hùng Vương, P.Mỹ Long, TP.Long Xuyên",               10.3763, 105.4350, 89, 884);
        Branch branch7  = saveBranch("BRANCH_07", "STORE", "Chi Nhánh Bến Tre",    "02753330005", "cn7@agrishrimp.vn",  "18 Nguyễn Đình Chiểu, P.1, TP.Bến Tre",                 10.2417, 106.3754, 83, 831);
        Branch branch8  = saveBranch("BRANCH_08", "STORE", "Chi Nhánh Trà Vinh",   "02943330006", "cn8@agrishrimp.vn",  "31 Điện Biên Phủ, P.4, TP.Trà Vinh",                    9.9513,  106.3420, 84, 842);
        Branch branch9  = saveBranch("BRANCH_09", "STORE", "Chi Nhánh Tiền Giang", "02733330007", "cn9@agrishrimp.vn",  "10 Lý Thường Kiệt, P.1, TP.Mỹ Tho",                    10.3600, 106.3600, 82, 826);
        Branch branch10 = saveBranch("BRANCH_10", "STORE", "Chi Nhánh Long An",    "02723330008", "cn10@agrishrimp.vn", "56 Hùng Vương, P.2, TP.Tân An",                          10.5351, 106.4053, 80, 799);
        Branch branch11 = saveBranch("BRANCH_11", "STORE", "Chi Nhánh Vĩnh Long",  "02703330009", "cn11@agrishrimp.vn", "22 Phạm Thái Bường, P.1, TP.Vĩnh Long",                 10.2566, 105.9721, 86, 861);
        Branch branch12 = saveBranch("BRANCH_12", "STORE", "Chi Nhánh Đồng Tháp",  "02773330010", "cn12@agrishrimp.vn", "88 Nguyễn Sinh Sắc, P.1, TP.Cao Lãnh",                  10.4591, 105.6349, 87, 871);

        // ── 2. NGƯỜI DÙNG DEMO ───────────────────────────────────────────
        User kho1   = saveUser("Trần Thị Kho Một",  "kho1@agrishrimp.vn",   "0901000002", "123456", managerRole,  mainWh,  Gender.FEMALE, LocalDate.of(1992,  7, 20));
        User kho2   = saveUser("Lê Văn Kho Hai",    "kho2@agrishrimp.vn",   "0901000003", "123456", managerRole,  branch1, Gender.MALE,   LocalDate.of(1993, 11,  5));
        User user1  = saveUser("Nguyễn Văn Tôm",    "user1@gmail.com",      "0911000001", "123456", userRole,     null,    Gender.MALE,   LocalDate.of(1988,  6, 12));
        User user2  = saveUser("Trần Thị Cua",      "user2@gmail.com",      "0911000002", "123456", userRole,     null,    Gender.FEMALE, LocalDate.of(1990,  9, 25));
        User user3  = saveUser("Lê Minh Nuôi",       "user3@gmail.com",       "0911000003", "123456", userRole,     null,     Gender.MALE,   LocalDate.of(1995,  2, 18));
        User user4  = saveUser("Phạm Hữu Cá",       "user4@gmail.com",       "0911000004", "123456", userRole,     null,     Gender.MALE,   LocalDate.of(1992,  5, 10));
        User kho3  = saveUser("Phạm Văn Bạc Liêu",  "kho3@agrishrimp.vn",   "0901000004", "123456", managerRole, branch3,  Gender.MALE,   LocalDate.of(1994,  3, 10));
        User kho4  = saveUser("Nguyễn Thị Cà Mau",  "kho4@agrishrimp.vn",   "0901000005", "123456", managerRole, branch4,  Gender.FEMALE, LocalDate.of(1991,  8, 22));
        User kho5  = saveUser("Lê Văn Kiên Giang",  "kho5@agrishrimp.vn",   "0901000006", "123456", staffRole,   branch5,  Gender.MALE,   LocalDate.of(1996,  5, 15));
        User kho6  = saveUser("Trần Thị An Giang",  "kho6@agrishrimp.vn",   "0901000007", "123456", staffRole,   branch6,  Gender.FEMALE, LocalDate.of(1993, 12,  3));
        User kho7  = saveUser("Võ Văn Bến Tre",     "kho7@agrishrimp.vn",   "0901000008", "123456", staffRole,   branch7,  Gender.MALE,   LocalDate.of(1995,  7, 18));
        User kho8  = saveUser("Huỳnh Thị Trà Vinh", "kho8@agrishrimp.vn",   "0901000009", "123456", staffRole,   branch8,  Gender.FEMALE, LocalDate.of(1992,  4, 30));
        User kho9  = saveUser("Dương Văn Tiền Giang","kho9@agrishrimp.vn",   "0901000010", "123456", staffRole,   branch9,  Gender.MALE,   LocalDate.of(1994,  9, 12));
        User kho10 = saveUser("Bùi Thị Long An",    "kho10@agrishrimp.vn",  "0901000011", "123456", staffRole,   branch10, Gender.FEMALE, LocalDate.of(1997,  1, 25));
        User kho11 = saveUser("Phan Văn Vĩnh Long", "kho11@agrishrimp.vn",  "0901000012", "123456", staffRole,   branch11, Gender.MALE,   LocalDate.of(1993,  6,  8));
        User kho12  = saveUser("Ngô Thị Đồng Tháp",    "kho12@agrishrimp.vn",  "0901000013", "123456", staffRole,   branch12, Gender.FEMALE, LocalDate.of(1996, 11, 17));
        User khoCn2 = saveUser("Nguyễn Văn Sóc Trăng", "cn2mgr@agrishrimp.vn", "0901000014", "123456", managerRole, branch2,  Gender.MALE,   LocalDate.of(1992,  4, 18));

        // ── 5. DANH MỤC  ──────────────────────────
        Category catFeed   = categoryRepository.save(cat("Thức Ăn Thủy Sản",         null, "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/cat-feed_pjm6uv.jpg"));
        Category catChem   = categoryRepository.save(cat("Thuốc & Chế Phẩm Sinh Học", null, "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/cat-chem_v8w3zv.jpg"));
        Category catMine   = categoryRepository.save(cat("Khoáng Chất & Dinh Dưỡng",  null, "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/cat-min_p8v6zv.jpg"));
        Category catEquip  = categoryRepository.save(cat("Dụng Cụ & Trang Thiết Bị",  null, "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/cat-equip_v8w3zv.jpg"));

        Category catSF     = categoryRepository.save(cat("Thức Ăn Tôm",               catFeed, null));
        Category catFF     = categoryRepository.save(cat("Thức Ăn Cá",                catFeed, null));
        Category catPro    = categoryRepository.save(cat("Chế Phẩm Vi Sinh",           catChem, null));
        Category catMed    = categoryRepository.save(cat("Thuốc Phòng Trị Bệnh",      catChem, null));
        Category catMin    = categoryRepository.save(cat("Khoáng Chất",               catMine, null));
        Category catVit    = categoryRepository.save(cat("Vitamin & Enzyme",           catMine, null));
        Category catMeas   = categoryRepository.save(cat("Dụng Cụ Đo Lường",          catEquip, null));
        Category catPondE  = categoryRepository.save(cat("Thiết Bị Ao Nuôi",          catEquip, null));

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
        AttributeValue avBox    = av(attrP, "Hộp giấy");
        AttributeValue av1kg    = av(attrW, "1kg");
        AttributeValue av10kg   = av(attrW, "10kg");
        AttributeValue av50kg   = av(attrW, "50kg");

        // ── 8. SẢN PHẨM & BIẾN THỂ (LƯU Ý: KHÔNG CÒN SET GIÁ) ────────────
        Product p1  = prod("Thức Ăn Tôm CP 8012", "thuc-an-tom-cp-8012",
                "Thức ăn viên chìm cho tôm thẻ", "Thức ăn tôm CP 8012...", bCP, catSF, "Việt Nam", "CP8012", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p1_pjm6uv.jpg");
        p1.getSuppliers().add(supCP);
        productRepository.save(p1);

        ProductVariant pv1a = pv(p1, "CP8012-5KG",  "8935001001011", VariantStatus.ACTIVE);
        ProductVariant pv1b = pv(p1, "CP8012-25KG", "8935001001012", VariantStatus.ACTIVE);
        skua(pv1a, attrW, av5kg);   skua(pv1a, attrP, avBag);
        skua(pv1b, attrW, av25kg);  skua(pv1b, attrP, avSack);

        Product p4  = prod("Chế Phẩm EM Xử Lý Ao Nuôi", "che-pham-em",
                "Vi khuẩn có lợi làm sạch đáy ao", "Chế phẩm EM chứa Bacillus...", bGN, catPro, "Việt Nam", "EM-XL", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p4_v8w3zv.jpg");
        p4.getSuppliers().add(supGN);
        productRepository.save(p4);

        ProductVariant pv4a = pv(p4, "EM-XL-500ML", "8935004001011", VariantStatus.ACTIVE);
        skua(pv4a, attrW, av500g);  skua(pv4a, attrP, avBottle);

        Product p2  = prod("Thức Ăn Tôm Tomboy #1 Ươm Giống", "thuc-an-tom-tomboy-1",
                "Thức ăn micro-pellet ươm giống tôm thẻ chân trắng", "Tomboy #1 giàu protein 42%, lipid 8%...", bTomboy, catSF, "Việt Nam", "TBY-1", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p2_p8v6zv.jpg");
        p2.getSuppliers().add(supTom);
        productRepository.save(p2);

        ProductVariant pv2a = pv(p2, "TBY-1-500G", "8935002001001", VariantStatus.ACTIVE);
        ProductVariant pv2b = pv(p2, "TBY-1-5KG",  "8935002001002", VariantStatus.ACTIVE);
        skua(pv2a, attrW, av500g); skua(pv2a, attrP, avBag);
        skua(pv2b, attrW, av5kg);  skua(pv2b, attrP, avBag);

        Product p3  = prod("Khoáng Tổng Hợp EDTA Agri-Min", "khoang-edta-tong-hop",
                "Bổ sung khoáng đa vi lượng Ca, Mg, K, Na cho ao tôm", "Khoáng EDTA hỗn hợp Ca-Mg-K-Na dạng bột tan nhanh...", bGN, catMin, "Việt Nam", "GN-EDTA", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p3_v8w3zv.jpg");
        p3.getSuppliers().add(supGN);
        productRepository.save(p3);

        ProductVariant pv3a = pv(p3, "GN-EDTA-1KG", "8935003001001", VariantStatus.ACTIVE);
        ProductVariant pv3b = pv(p3, "GN-EDTA-5KG", "8935003001002", VariantStatus.ACTIVE);
        skua(pv3a, attrW, av1kg);  skua(pv3a, attrP, avBag);
        skua(pv3b, attrW, av5kg);  skua(pv3b, attrP, avSack);

        Product p5  = prod("Vitamin C Aqua Stable", "vitamin-c-aqua-stable",
                "Vitamin C bền vững trong nước, tăng sức đề kháng tôm", "Vitamin C 35% dạng bột, bền pH 5-8, không bị oxy hóa nhanh...", bGN, catVit, "Việt Nam", "GN-VTC", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p5_pjm6uv.jpg");
        p5.getSuppliers().add(supGN);
        productRepository.save(p5);

        ProductVariant pv5a = pv(p5, "GN-VTC-500G", "8935005001001", VariantStatus.ACTIVE);
        ProductVariant pv5b = pv(p5, "GN-VTC-5KG",  "8935005001002", VariantStatus.ACTIVE);
        skua(pv5a, attrW, av500g); skua(pv5a, attrP, avBag);
        skua(pv5b, attrW, av5kg);  skua(pv5b, attrP, avSack);

        Product p6  = prod("Vôi Dolomite Cân Bằng pH Ao", "voi-dolomite-xu-ly-ao",
                "Cân bằng pH đáy ao, kiềm hóa môi trường nuôi tôm", "Vôi dolomite CaMg(CO3)2 nghiền mịn, độ tinh khiết >95%...", bCP, catMin, "Việt Nam", "CP-DOLO", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p6_v8w3zv.jpg");
        p6.getSuppliers().add(supCP);
        productRepository.save(p6);

        ProductVariant pv6a = pv(p6, "CP-DOLO-25KG", "8935006001001", VariantStatus.ACTIVE);
        ProductVariant pv6b = pv(p6, "CP-DOLO-50KG", "8935006001002", VariantStatus.ACTIVE);
        skua(pv6a, attrW, av25kg); skua(pv6a, attrP, avSack);
        skua(pv6b, attrW, av50kg); skua(pv6b, attrP, avSack);

        Product p7  = prod("Bacillus BioPlus Vi Sinh Đáy Ao", "bacillus-bioplus",
                "Vi sinh phân giải khí độc H2S, NH3 tích tụ đáy ao", "Bacillus subtilis mật độ 10^9 CFU/g, Bacillus licheniformis...", bGN, catPro, "Việt Nam", "GN-BIO", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p7_p8v6zv.jpg");
        p7.getSuppliers().add(supGN);
        productRepository.save(p7);

        ProductVariant pv7a = pv(p7, "GN-BIO-500G", "8935007001001", VariantStatus.ACTIVE);
        ProductVariant pv7b = pv(p7, "GN-BIO-5KG",  "8935007001002", VariantStatus.ACTIVE);
        skua(pv7a, attrW, av500g); skua(pv7a, attrP, avBottle);
        skua(pv7b, attrW, av5kg);  skua(pv7b, attrP, avSack);

        Product p8  = prod("OTC-80 Oxytetracycline Kháng Khuẩn", "otc-80-oxytetracycline",
                "Kháng khuẩn phổ rộng cho tôm cá bị đốm trắng, hoại tử", "Oxytetracycline HCl 80% dạng bột tan nước, phổ kháng khuẩn rộng...", bCP, catMed, "Việt Nam", "CP-OTC", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p8_v8w3zv.jpg");
        p8.getSuppliers().add(supCP);
        productRepository.save(p8);

        ProductVariant pv8a = pv(p8, "CP-OTC-500G", "8935008001001", VariantStatus.ACTIVE);
        ProductVariant pv8b = pv(p8, "CP-OTC-1KG",  "8935008001002", VariantStatus.ACTIVE);
        skua(pv8a, attrW, av500g); skua(pv8a, attrP, avBox);
        skua(pv8b, attrW, av1kg);  skua(pv8b, attrP, avBox);

        Product p9  = prod("Thức Ăn Cá Da Trơn Tomboy 26%", "thuc-an-ca-da-tron-tomboy-26",
                "Thức ăn viên nổi cho cá tra, cá basa, protein 26%", "Tomboy 26% protein, lipid 5%, dạng viên nổi 2.5mm...", bTomboy, catFF, "Việt Nam", "TBY-CAT", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p9_pjm6uv.jpg");
        p9.getSuppliers().add(supTom);
        productRepository.save(p9);

        ProductVariant pv9a = pv(p9, "TBY-CAT-5KG",  "8935009001001", VariantStatus.ACTIVE);
        ProductVariant pv9b = pv(p9, "TBY-CAT-25KG", "8935009001002", VariantStatus.ACTIVE);
        skua(pv9a, attrW, av5kg);  skua(pv9a, attrP, avBag);
        skua(pv9b, attrW, av25kg); skua(pv9b, attrP, avSack);

        Product p10 = prod("Máy Đo pH-DO-Nhiệt Độ Cầm Tay", "may-do-ph-do-nhiet-do",
                "Thiết thiết bị đo đa chỉ tiêu: pH, oxy hòa tan, nhiệt độ ao nuôi", "Độ chính xác pH ±0.01, DO ±0.1 mg/L, Temp ±0.5°C, chống nước IP67...", bGN, catMeas, "Nhật Bản", "GN-METER", "https://res.cloudinary.com/dcl7geun3/image/upload/v1731514488/p10_v8w3zv.jpg");
        p10.getSuppliers().add(supGN);
        productRepository.save(p10);

        // Define pv10a since it was used below but removed accidentally in last edit
        ProductVariant pv10a = pv(p10, "GN-METER-01", "8935010001001", VariantStatus.ACTIVE);

        // ── 9. NHÀ CUNG CẤP (Already moved to top) ───────────────────────
        // (Removal of duplicate supplier creation)

        // ── 10. TỒN KHO THEO LÔ (GÁN GIÁ VỐN + BATCH VÀO INVENTORY) ──────
        LocalDateTime now = LocalDateTime.now();
        // Lô hàng 1: Nhập tháng trước, giá rẻ hơn
        inv(pv1a, mainWh, 50, bd(140_000), "LOT-CP-OLD", 10, now, kho1);
        inv(pv1b, mainWh, 20, bd(660_000), "LOT-CP-OLD", 5, now, kho1);

        // Lô hàng 2: Nhập mới, giá cao hơn
        inv(pv1a, mainWh, 100, bd(145_000), "LOT-CP-NEW", 10, now, kho1);

        inv(pv1a, branch1, 30, bd(140_000), "LOT-CP-OLD", 5, now, kho2);
        inv(pv4a, branch1, 50, bd(62_000), "LOT-EM-01", 10, now, kho2);

        inv(pv1a, branch2, 20, bd(142_000), "LOT-CP-MID", 5, now, khoCn2);

        // Thêm tồn kho sản phẩm mới cho kho tổng và chi nhánh cũ
        inv(pv2b,  mainWh,  150, bd(130_000),   "LOT-MAIN-TBY-001",   20, now, kho1);
        inv(pv3b,  mainWh,  100, bd(380_000),   "LOT-MAIN-MIN-001",   10, now, kho1);
        inv(pv5b,  mainWh,   80, bd(330_000),   "LOT-MAIN-VIT-001",   10, now, kho1);
        inv(pv6a,  mainWh,  200, bd(85_000),    "LOT-MAIN-VOIA-001",  20, now, kho1);
        inv(pv7b,  mainWh,   80, bd(950_000),   "LOT-MAIN-BIO-001",   10, now, kho1);
        inv(pv8a,  mainWh,   50, bd(280_000),   "LOT-MAIN-OTC-001",    5, now, kho1);
        inv(pv9b,  mainWh,   80, bd(550_000),   "LOT-MAIN-CAT-001",   10, now, kho1);
        inv(pv10a, mainWh,    5, bd(3_500_000), "LOT-MAIN-METER-001",  1, now, kho1);

        inv(pv2b,  branch1, 80, bd(130_000), "LOT-CN1-TBY-001",  10, now, kho2);
        inv(pv3a,  branch1, 60, bd(85_000),  "LOT-CN1-MIN-001",  10, now, kho2);
        inv(pv5a,  branch1,100, bd(75_000),  "LOT-CN1-VIT-001",  15, now, kho2);
        inv(pv6a,  branch1, 50, bd(85_000),  "LOT-CN1-VOIA-001", 10, now, kho2);
        inv(pv7a,  branch1, 40, bd(220_000), "LOT-CN1-BIO-001",  10, now, kho2);
        inv(pv2a,  branch1, 60, bd(35_000),  "LOT-CN1-TBY-500G-001", 10, now, kho2);

        inv(pv2a,  branch2, 40, bd(35_000),  "LOT-CN2-TBY-500G-001", 10, now, khoCn2);
        inv(pv2b,  branch2, 40, bd(130_000), "LOT-CN2-TBY-001",  10, now, khoCn2);
        inv(pv5b,  branch2, 50, bd(330_000), "LOT-CN2-VIT-001",  10, now, khoCn2);
        inv(pv6a,  branch2, 80, bd(85_000),  "LOT-CN2-VOIA-001", 10, now, khoCn2);

        // Tồn kho cho 10 chi nhánh mới (từ phiếu nhập COMPLETED)
        inv(pv1a,  branch3, 120, bd(145_000), "LOT-CN3-CP-001",   15, now, kho3);
        inv(pv1b,  branch3,  60, bd(660_000), "LOT-CN3-CP-001",    5, now, kho3);
        inv(pv4a,  branch3,  80, bd(62_000),  "LOT-CN3-EM-001",   10, now, kho3);
        inv(pv7a,  branch3,  50, bd(220_000), "LOT-CN3-BIO-001",  10, now, kho3);

        inv(pv1a,  branch4, 200, bd(145_000), "LOT-CN4-CP-001",   20, now, kho4);
        inv(pv1b,  branch4, 100, bd(660_000), "LOT-CN4-CP-001",   10, now, kho4);
        inv(pv2b,  branch4, 100, bd(130_000), "LOT-CN4-TBY-001",  15, now, kho4);
        inv(pv4a,  branch4, 100, bd(62_000),  "LOT-CN4-EM-001",   15, now, kho4);

        inv(pv1a,  branch5, 100, bd(145_000), "LOT-CN5-CP-001",   15, now, kho5);
        inv(pv2b,  branch5,  50, bd(130_000), "LOT-CN5-TBY-001",  10, now, kho5);
        inv(pv4a,  branch5,  60, bd(62_000),  "LOT-CN5-EM-001",   10, now, kho5);
        inv(pv7a,  branch5,  80, bd(220_000), "LOT-CN5-BIO-001",  10, now, kho5);

        inv(pv9a,  branch6,  80, bd(120_000), "LOT-CN6-CAT-001",  15, now, kho6);
        inv(pv9b,  branch6,  40, bd(550_000), "LOT-CN6-CAT-001",   5, now, kho6);
        inv(pv3a,  branch6,  60, bd(85_000),  "LOT-CN6-MIN-001",  10, now, kho6);
        inv(pv5a,  branch6,  80, bd(75_000),  "LOT-CN6-VIT-001",  10, now, kho6);

        inv(pv1a,  branch7,  90, bd(145_000), "LOT-CN7-CP-001",   15, now, kho7);
        inv(pv1b,  branch7,  45, bd(660_000), "LOT-CN7-CP-001",    5, now, kho7);
        inv(pv4a,  branch7,  70, bd(62_000),  "LOT-CN7-EM-001",   10, now, kho7);

        inv(pv1a,  branch8,  80, bd(145_000), "LOT-CN8-CP-001",   10, now, kho8);
        inv(pv2b,  branch8,  40, bd(130_000), "LOT-CN8-TBY-001",  10, now, kho8);
        inv(pv4a,  branch8,  60, bd(62_000),  "LOT-CN8-EM-001",   10, now, kho8);
        inv(pv5a,  branch8,  60, bd(75_000),  "LOT-CN8-VIT-001",  10, now, kho8);

        inv(pv9a,  branch9,  70, bd(120_000), "LOT-CN9-CAT-001",  15, now, kho9);
        inv(pv9b,  branch9,  30, bd(550_000), "LOT-CN9-CAT-001",   5, now, kho9);
        inv(pv1a,  branch9,  80, bd(145_000), "LOT-CN9-CP-001",   15, now, kho9);
        inv(pv2a,  branch9,  50, bd(35_000),  "LOT-CN9-TBY-001",  20, now, kho9);

        inv(pv1a,  branch10, 100, bd(145_000), "LOT-CN10-CP-001",  15, now, kho10);
        inv(pv2b,  branch10,  50, bd(130_000), "LOT-CN10-TBY-001", 10, now, kho10);
        inv(pv3a,  branch10,  80, bd(85_000),  "LOT-CN10-MIN-001", 10, now, kho10);
        inv(pv4a,  branch10,  60, bd(62_000),  "LOT-CN10-EM-001",  10, now, kho10);

        inv(pv1a,  branch11,  90, bd(145_000), "LOT-CN11-CP-001",  15, now, kho11);
        inv(pv9a,  branch11,  60, bd(120_000), "LOT-CN11-CAT-001", 10, now, kho11);
        inv(pv4a,  branch11,  50, bd(62_000),  "LOT-CN11-EM-001",  10, now, kho11);
        inv(pv5a,  branch11,  70, bd(75_000),  "LOT-CN11-VIT-001", 10, now, kho11);

        inv(pv9a,  branch12, 120, bd(120_000), "LOT-CN12-CAT-001", 20, now, kho12);
        inv(pv9b,  branch12,  60, bd(550_000), "LOT-CN12-CAT-001",  5, now, kho12);
        inv(pv3a,  branch12,  80, bd(85_000),  "LOT-CN12-MIN-001", 10, now, kho12);
        inv(pv6a,  branch12,  60, bd(85_000),  "LOT-CN12-VOIA-001",10, now, kho12);

        // ── 11. PHIẾU NHẬP HÀNG (InventoryNote + Details) ──────────────
        // Kho Tổng — 3 phiếu (2 đã duyệt, 1 chờ duyệt)
        InventoryNote nMain1 = saveNote("PN-MAIN-001", mainWh,  supCP,  kho1, now.minusDays(60), InventoryNoteStatus.COMPLETED, bd(101_300_000));
        addDetail(nMain1, pv1a, 200, bd(145_000), "LOT-MAIN-CP-001");
        addDetail(nMain1, pv1b,  80, bd(660_000), "LOT-MAIN-CP-001");
        addDetail(nMain1, pv2b, 150, bd(130_000), "LOT-MAIN-TBY-001");

        InventoryNote nMain2 = saveNote("PN-MAIN-002", mainWh,  supGN,  kho1, now.minusDays(30), InventoryNoteStatus.COMPLETED, bd(157_400_000));
        addDetail(nMain2, pv3b, 100, bd(380_000), "LOT-MAIN-MIN-001");
        addDetail(nMain2, pv5b,  80, bd(330_000), "LOT-MAIN-VIT-001");
        addDetail(nMain2, pv6a, 200, bd(85_000),  "LOT-MAIN-VOIA-001");
        addDetail(nMain2, pv7b,  80, bd(950_000), "LOT-MAIN-BIO-001");

        InventoryNote nMain3 = saveNote("PN-MAIN-003", mainWh,  supCP,  kho1, now.minusDays(14), InventoryNoteStatus.PENDING,   bd(40_800_000));
        addDetail(nMain3, pv8a,   50, bd(280_000),   "LOT-MAIN-OTC-002");
        addDetail(nMain3, pv4a,  150, bd(62_000),    "LOT-MAIN-EM-002");
        addDetail(nMain3, pv10a,   5, bd(3_500_000), "LOT-MAIN-METER-001");

        // Chi Nhánh Cần Thơ — 3 phiếu
        InventoryNote nCn1a = saveNote("PN-CN1-001", branch1, supCP,  kho2, now.minusDays(55), InventoryNoteStatus.COMPLETED, bd(44_120_000));
        addDetail(nCn1a, pv1a, 100, bd(140_000), "LOT-CN1-CP-001");
        addDetail(nCn1a, pv1b,  40, bd(660_000), "LOT-CN1-CP-001");
        addDetail(nCn1a, pv4a,  60, bd(62_000),  "LOT-CN1-EM-001");

        InventoryNote nCn1b = saveNote("PN-CN1-002", branch1, supGN,  kho2, now.minusDays(28), InventoryNoteStatus.COMPLETED, bd(23_000_000));
        addDetail(nCn1b, pv2b,  80, bd(130_000), "LOT-CN1-TBY-001");
        addDetail(nCn1b, pv3a,  60, bd(85_000),  "LOT-CN1-MIN-001");
        addDetail(nCn1b, pv5a, 100, bd(75_000),  "LOT-CN1-VIT-001");

        InventoryNote nCn1c = saveNote("PN-CN1-003", branch1, supMW,  kho2, now.minusDays(7),  InventoryNoteStatus.PENDING,   bd(13_050_000));
        addDetail(nCn1c, pv6a, 50, bd(85_000),  "LOT-CN1-VOIA-002");
        addDetail(nCn1c, pv7a, 40, bd(220_000), "LOT-CN1-BIO-001");

        // ── 12. KHÁCH HÀNG ──────────────────────────────────────────────
        customerRepository.save(Customer.builder().user(user1).name("Nguyễn Văn Tôm").phone("0911000001").email("user1@gmail.com").gender(CustomerGender.MALE).provinceId("79").addressDetail("Cà Mau").status(CustomerStatus.ACTIVE).build());
        customerRepository.save(Customer.builder().user(user2).name("Trần Thị Cua").phone("0911000002").email("user2@gmail.com").gender(CustomerGender.FEMALE).provinceId("94").addressDetail("Sóc Trăng").status(CustomerStatus.ACTIVE).build());
        customerRepository.save(Customer.builder().user(user3).name("Lê Minh Nuôi").phone("0911000003").email("user3@gmail.com").gender(CustomerGender.MALE).provinceId("95").addressDetail("Bạc Liêu").status(CustomerStatus.ACTIVE).build());
        customerRepository.save(Customer.builder().user(user4).name("Phạm Hữu Cá").phone("0911000004").email("user4@gmail.com").gender(CustomerGender.MALE).provinceId("92").addressDetail("Cần Thơ").status(CustomerStatus.ACTIVE).build());


        // ── 13. ĐƠN HÀNG ────────────────────────────────────────────────
        // Đơn 1: Cần Thơ, đã giao
        Order o1 = orderRepository.save(Order.builder()
                .code("DH-2024-001").branch(branch1).user(user1).status(OrderStatus.COMPLETED)
                .paymentMethod(PaymentMethod.COD).paymentStatus(PaymentStatus.PAID).shippingAddress("Cà Mau")
                .totalAmount(bd(546_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(546_000))
                .createdAt(now.minusDays(15)).build());
        orderItemRepository.save(oi(o1, pv1a, 3, bd(182_000)));

        // Đơn 2: Sóc Trăng, đang giao
        Order o2 = orderRepository.save(Order.builder()
                .code("DH-2024-002").branch(branch2).user(user2).status(OrderStatus.SHIPPING)
                .paymentMethod(PaymentMethod.COD).paymentStatus(PaymentStatus.UNPAID).shippingAddress("Sóc Trăng")
                .totalAmount(bd(1_144_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(1_144_000))
                .createdAt(now.minusDays(2)).build());
        orderItemRepository.save(oi(o2, pv1a, 2, bd(182_000)));
        orderItemRepository.save(oi(o2, pv2b, 6, bd(130_000)));

        // Đơn 3: Bạc Liêu, mới tạo
        Order o3 = orderRepository.save(Order.builder()
                .code("DH-2024-003").branch(branch3).user(user3).status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD).paymentStatus(PaymentStatus.UNPAID).shippingAddress("Bạc Liêu")
                .totalAmount(bd(2_500_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(2_500_000))
                .createdAt(now.minusHours(5)).build());
        orderItemRepository.save(oi(o3, pv1b, 2, bd(850_000)));
        orderItemRepository.save(oi(o3, pv7a, 4, bd(200_000)));

        // Đơn 4: Cần Thơ, đã xác nhận
        Order o4 = orderRepository.save(Order.builder()
                .code("DH-2024-004").branch(branch1).user(user4).status(OrderStatus.CONFIRMED)
                .paymentMethod(PaymentMethod.PAYOS).paymentStatus(PaymentStatus.PAID).shippingAddress("Cần Thơ")
                .totalAmount(bd(750_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(750_000))
                .createdAt(now.minusDays(1)).build());
        orderItemRepository.save(oi(o4, pv5a, 10, bd(75_000)));

        log.info(">>> KHỞI TẠO DỮ LIỆU DEMO HOÀN TẤT.");
    }

    // ====================================================================
    // PRIVATE HELPERS
    // ====================================================================

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
        return roleRepository.findBySlug(slug)
                .map(existingRole -> {
                    existingRole.setDisplayName(displayName);
                    existingRole.setIsSystem(isSystem);
                    existingRole.setIsActive(true);
                    existingRole.setDescription("Vai trò " + displayName);
                    existingRole.setPermissions(perms);
                    return roleRepository.save(existingRole);
                })
                .orElseGet(() ->
                        roleRepository.save(Role.builder()
                                .slug(slug).displayName(displayName).isSystem(isSystem).isActive(true)
                                .description("Vai trò " + displayName).permissions(perms).build()));
    }

    private Branch saveBranch(String code, String type, String name, String phone, String email, String address, Double lat, Double lng, Integer provinceId, Integer districtId) {
        return branchRepository.findByBranchCode(code).orElseGet(() ->
                branchRepository.save(Branch.builder()
                        .branchCode(code).branchType(type).name(name)
                        .phone(phone).email(email).addressDetail(address)
                        .lat(lat).lng(lng)
                        .provinceId(provinceId).districtId(districtId)
                        .status(BranchStatus.ACTIVE).build()));
    }

    private User saveUser(String name, String email, String phone, String pass,
                          Role role, Branch branch, Gender gender, LocalDate dob) {
        if (userRepository.existsByEmail(email)) return userRepository.findByEmail(email).orElse(null);
        return userRepository.save(User.builder()
                .fullName(name).email(email).phoneNumber(phone)
                .passwordHash(passwordEncoder.encode(pass))
                .status(UserStatus.ACTIVE).role(role).branch(branch)
                .gender(gender).dateOfBirth(dob).provider(AuthProvider.LOCAL).build());
    }

    private Category cat(String name, Category parent, String imageUrl) {
        return Category.builder()
                .name(name)
                .parent(parent)
                .imageUrl(imageUrl)
                .status(CategoryStatus.ACTIVE)
                .build();
    }

    private AttributeValue av(Attribute attr, String value) {
        return attributeValueRepository.save(
                AttributeValue.builder().attribute(attr).value(value).build());
    }

    private Product prod(String name, String slug, String shortDesc, String desc,
                         Brand brand, Category cat, String origin, String baseSku, String imageUrl) {
        Product p = productRepository.findBySlug(slug).orElseGet(() ->
                productRepository.save(Product.builder()
                        .name(name).slug(slug).shortDesc(shortDesc).description(desc)
                        .brand(brand).category(cat).origin(origin).baseSku(baseSku)
                        .status(ProductStatus.ACTIVE).ratingAverage(4.5f).reviewCount(10)
                        .createdAt(LocalDateTime.now()).build()));

        if (imageUrl != null && !imageUrl.isEmpty()) {
            productImageRepository.save(ProductImage.builder()
                    .imageUrl(imageUrl).product(p).build());
        }
        return p;
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

    private void inv(ProductVariant variant, Branch branch, int qty, BigDecimal importPrice, String batchCode, int minStock, LocalDateTime now, User user) {
        Inventory inventory = inventoryRepository.save(Inventory.builder()
                .productVariant(variant).branch(branch).quantity(qty)
                .importPrice(importPrice).batchNumber(batchCode)
                .minStock(minStock).lastCheckedAt(now).lastReceiptDate(now)
                .expiryDate(now.plusMonths(24))
                .build());

        inventoryTransactionRepository.save(InventoryTransaction.builder()
                .type(TransactionType.IMPORT)
                .quantityChange(qty)
                .newBalance(qty)
                .referenceCode(batchCode)
                .reason("Khởi tạo dữ liệu demo")
                .createdAt(now)
                .inventory(inventory)
                .createdBy(user)
                .build());
    }

    private OrderItem oi(Order order, ProductVariant variant, int qty, BigDecimal price) {
        return OrderItem.builder().order(order).productVariant(variant)
                .quantity(qty).price(price).build();
    }

    private BigDecimal bd(double val) {
        return BigDecimal.valueOf(val);
    }

    private InventoryNote saveNote(String code, Branch branch, Supplier supplier, User createdBy,
                                   LocalDateTime date, InventoryNoteStatus status, BigDecimal totalAmount) {
        BigDecimal paid = status == InventoryNoteStatus.COMPLETED ? totalAmount : BigDecimal.ZERO;
        return inventoryNoteRepository.save(InventoryNote.builder()
                .code(code).type(InventoryNoteType.IMPORT).reason("Nhập hàng định kỳ").status(status)
                .branch(branch).supplier(supplier).createdBy(createdBy)
                .createdAt(date).entryDate(date).deliverer("Tài xế giao hàng NCC")
                .totalAmount(totalAmount).paymentAmount(paid)
                .debtAmount(totalAmount.subtract(paid)).note("").build());
    }

    private void addDetail(InventoryNote note, ProductVariant variant, int qty, BigDecimal price, String batch) {
        inventoryNoteDetailRepository.save(InventoryNoteDetail.builder()
                .inventoryNote(note).productVariant(variant)
                .quantity(qty).quantityRequested(qty).quantityReal(qty)
                .price(price).batchNumber(batch)
                .expiryDate(LocalDateTime.now().plusMonths(18))
                .newSellingPrice(price.multiply(bd(1.35))).build());
    }
}

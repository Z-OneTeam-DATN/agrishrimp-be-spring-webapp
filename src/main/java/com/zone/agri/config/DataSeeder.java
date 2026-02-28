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
        log.info(">>> BẮT ĐẦU KHỞI TẠO DỮ LIỆU DEMO...");

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
        Permission mOrder = pMod("Quản lý đơn hàng", "ORDER", PermissionGroup.SALES);
        Permission aOrdV  = pAct("Xem đơn hàng", "ORDER_VIEW", PermissionGroup.SALES, mOrder);
        Permission aOrdC  = pAct("Tạo đơn hàng", "ORDER_CREATE", PermissionGroup.SALES, mOrder);
        Permission aOrdU  = pAct("Cập nhật đơn hàng", "ORDER_UPDATE", PermissionGroup.SALES, mOrder);
        Permission aOrdCnf = pAct("Xác nhận đơn hàng", "ORDER_CONFIRM", PermissionGroup.SALES, mOrder);
        Permission aOrdShip = pAct("Giao hàng", "ORDER_SHIP", PermissionGroup.SALES, mOrder);
        Permission aOrdX  = pAct("Huỷ đơn hàng", "ORDER_CANCEL", PermissionGroup.SALES, mOrder);
        Permission aOrdDone = pAct("Hoàn tất đơn hàng", "ORDER_COMPLETE", PermissionGroup.SALES, mOrder);
        Permission aOrdExport = pAct("Xuất danh sách đơn hàng", "ORDER_EXPORT", PermissionGroup.SALES, mOrder);
        Permission aOrdRefund = pAct("Hoàn tiền đơn hàng", "ORDER_REFUND", PermissionGroup.SALES, mOrder);
        Permission aOrdD = pAct("Xóa đơn hàng", "ORDER_DELETE", PermissionGroup.SALES, mOrder);

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
        Role adminRole = saveRole("ADMIN","Quản trị viên",false,Set.of(
                aDashV,aWspaceV,
                aRptSale,aRptInv,aRptFin,
                aUserV,aUserC,aUserU,aUserD,
                aRoleV,aRoleC,aRoleU,aRoleD,
                aBranchV,aBranchC,aBranchU,aBranchD,
                aProdV,aProdC,aProdU,aProdD,
                aCatV,aCatC,aCatU,aCatD,
                aAttrV,aAttrC,aAttrU,aAttrD,
                aImpV,aImpC,aImpA,aImpU,aImpX,aImpD,
                aExpV,aExpC,aExpA,aExpU,aExpX,aExpD,
                aTrfV,aTrfC,aTrfA,aTrfU,aTrfX,aTrfD,
                aCusV,aCusC,aCusU,aCusD,
                aSupV,aSupC,aSupU,aSupD,
                aOrdV,aOrdC,aOrdU,aOrdD,aOrdCnf,aOrdShip,aOrdX,aOrdDone,aOrdRefund,aOrdExport
        ));
        Role managerRole = saveRole("MANAGER","Quản lý chi nhánh & kho",false,Set.of(
                aWspaceV,
                aRptSale,aRptInv,aRptFin,
                aUserV,aUserC,aUserU,aUserD,
                aProdV,
                aCatV,
                aAttrV,
                aSupV,
                aImpV,aImpC,aImpU,aImpX,aImpD,
                aExpV,aExpC,aExpU,aExpX,aExpD,
                aTrfV,aTrfC,aTrfU,aTrfX,aTrfD,
                aCusV,aCusC,aCusU,aCusD,
                aOrdV,aOrdC,aOrdU,aOrdCnf,aOrdShip,aOrdX,aOrdDone,aOrdExport
        ));

        Role staffRole = saveRole("STAFF","Nhân viên",false,Set.of(
                aDashV,aWspaceV,
                aRptInv,
                aProdV,
                aCatV,
                aOrdV,aOrdC
        ));

        Role userRole = saveRole("USER","Khách hàng",false,Set.of(aOrdV,aOrdC,aOrdX));


        // ── 4. NGƯỜI DÙNG ────────────────────────────────────────────────
        User admin  = saveUser("Nguyễn Văn Admin",  "admin@agrishrimp.vn",  "0901000001", "123456", adminRole,    mainWh,  Gender.MALE,   LocalDate.of(1985,  3, 15));
        User kho1   = saveUser("Trần Thị Kho Một",  "kho1@agrishrimp.vn",   "0901000002", "123456", managerRole, mainWh,  Gender.FEMALE, LocalDate.of(1992,  7, 20));
        User kho2   = saveUser("Lê Văn Kho Hai",    "kho2@agrishrimp.vn",   "0901000003", "123456", managerRole, branch1, Gender.MALE,   LocalDate.of(1993, 11,  5));
        User user1  = saveUser("Nguyễn Văn Tôm",    "user1@gmail.com",      "0911000001", "123456",  userRole,     null,    Gender.MALE,   LocalDate.of(1988,  6, 12));
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
        Brand bCP      = brandRepository.save(Brand.builder().name("CP Vietnam")   .status(BrandStatus.ACTIVE).build());
        Brand bTomboy  = brandRepository.save(Brand.builder().name("Tomboy Feed")  .status(BrandStatus.ACTIVE).build());
        Brand bUni     = brandRepository.save(Brand.builder().name("Uni-President").status(BrandStatus.ACTIVE).build());
        Brand bGrobest = brandRepository.save(Brand.builder().name("Grobest")      .status(BrandStatus.ACTIVE).build());
        Brand bGN      = brandRepository.save(Brand.builder().name("Green Nature") .status(BrandStatus.ACTIVE).build());

        // ── 7. THUỘC TÍNH & GIÁ TRỊ ──────────────────────────────────────
        // Chỉ tạo thông tin chung (vỏ) của thuộc tính
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
        AttributeValue av1kg   = av(attrW, "1kg");
        AttributeValue av5kg   = av(attrW, "5kg");
        AttributeValue av10kg  = av(attrW, "10kg");
        AttributeValue av25kg  = av(attrW, "25kg");

        AttributeValue avBag   = av(attrP, "Túi PE");
        AttributeValue avSack  = av(attrP, "Bao PP");
        AttributeValue avBottle= av(attrP, "Chai nhựa");

        // ── 8. SẢN PHẨM & BIẾN THỂ ──────────────────────────────────────
        // P1: Thức ăn tôm CP 8012
        Product p1  = prod("Thức Ăn Tôm CP 8012", "thuc-an-tom-cp-8012",
            "Thức ăn viên chìm cho tôm thẻ giai đoạn nuôi thịt, size 12",
            "Thức ăn tôm CP 8012 được sản xuất theo công nghệ hiện đại tại nhà máy đạt chuẩn ISO. Chứa 40% protein, 7% lipid, đầy đủ vitamin và khoáng chất thiết yếu giúp tôm tăng trưởng nhanh, FCR thấp.",
            bCP, catSF, "Việt Nam", "CP8012");
        ProductVariant pv1a = pv(p1, "CP8012-5KG",  "8935001001011", 180_000, 140_000, 170_000, 5.0,  "5kg",  VariantStatus.ACTIVE);
        ProductVariant pv1b = pv(p1, "CP8012-25KG", "8935001001012", 850_000, 660_000, 800_000, 25.0, "25kg", VariantStatus.ACTIVE);
        skua(pv1a, attrW, av5kg);   skua(pv1a, attrP, avBag);
        skua(pv1b, attrW, av25kg);  skua(pv1b, attrP, avSack);

        // P2: Thức ăn tôm Tomboy T12
        Product p2  = prod("Thức Ăn Tôm Tomboy T12", "thuc-an-tom-tomboy-t12",
            "Thức ăn tôm Tomboy T12 chất lượng cao, protein ≥38%",
            "Tomboy T12 dạng viên chìm bền trong nước, ít thất thoát. Protein ≥38%, lipid ≥6%, độ ẩm ≤11%. Phù hợp tôm thẻ từ 3 gram đến thu hoạch.",
            bTomboy, catSF, "Việt Nam", "TOM-T12");
        ProductVariant pv2a = pv(p2, "TOM-T12-5KG",  "8935002001011", 165_000, 128_000, 155_000, 5.0,  "5kg",  VariantStatus.ACTIVE);
        ProductVariant pv2b = pv(p2, "TOM-T12-25KG", "8935002001012", 780_000, 605_000, 735_000, 25.0, "25kg", VariantStatus.ACTIVE);
        skua(pv2a, attrW, av5kg);   skua(pv2a, attrP, avBag);
        skua(pv2b, attrW, av25kg);  skua(pv2b, attrP, avSack);

        // P3: Thức ăn cá Uni-Feed nổi 3mm
        Product p3  = prod("Thức Ăn Cá Uni-Feed Nổi 3mm", "thuc-an-ca-uni-feed-noi-3mm",
            "Viên nổi 3mm cho cá tra, cá rô phi giai đoạn lớn",
            "Uni-Feed Nổi 3mm protein 28%, lipid 5%. Viên nổi giúp kiểm soát lượng ăn, giảm ô nhiễm ao. Phù hợp cá tra, cá rô phi, cá điêu hồng từ 50g trở lên.",
            bUni, catFF, "Việt Nam", "UNI-FISH");
        ProductVariant pv3a = pv(p3, "UNI-FISH-5KG",  "8935003001011", 140_000, 108_000, 132_000, 5.0,  "5kg",  VariantStatus.ACTIVE);
        ProductVariant pv3b = pv(p3, "UNI-FISH-25KG", "8935003001012", 660_000, 510_000, 620_000, 25.0, "25kg", VariantStatus.ACTIVE);
        skua(pv3a, attrW, av5kg);   skua(pv3a, attrP, avBag);
        skua(pv3b, attrW, av25kg);  skua(pv3b, attrP, avSack);

        // P4: Chế phẩm EM xử lý ao nuôi
        Product p4  = prod("Chế Phẩm EM Xử Lý Ao Nuôi", "che-pham-em-xu-ly-ao-nuoi",
            "Vi khuẩn có lợi làm sạch đáy ao, giảm khí độc H₂S, NH₃",
            "Chế phẩm EM chứa Bacillus subtilis ≥10⁸ CFU/ml, Lactobacillus spp., Saccharomyces cerevisiae. Giảm khí độc, cải thiện màu nước, ổn định pH, phân giải mùn bã hữu cơ đáy ao hiệu quả.",
            bGN, catPro, "Việt Nam", "EM-XL");
        ProductVariant pv4a = pv(p4, "EM-XL-500ML", "8935004001011",  85_000,  62_000,  78_000, 0.5,  "500ml", VariantStatus.ACTIVE);
        ProductVariant pv4b = pv(p4, "EM-XL-1L",    "8935004001012", 155_000, 112_000, 145_000, 1.0,  "1L",    VariantStatus.ACTIVE);
        skua(pv4a, attrW, av500g);  skua(pv4a, attrP, avBottle);
        skua(pv4b, attrW, av1kg);   skua(pv4b, attrP, avBottle);

        // P5: Iodine sát khuẩn 10%
        Product p5  = prod("Iodine Sát Khuẩn Ao Nuôi 10%", "iodine-sat-khuan-ao-nuoi-10",
            "Dung dịch Iodine 10% diệt khuẩn, vi nấm, virus trong ao",
            "Iodine Grobest 10% (Povidone Iodine) diệt khuẩn phổ rộng, không gây kháng thuốc. Dùng để sát trùng ao trước vụ nuôi, xử lý khi tôm bị bệnh do vi khuẩn và virus.",
            bGrobest, catMed, "Đài Loan", "IODIN10");
        ProductVariant pv5a = pv(p5, "IODIN10-500ML", "8935005001011",  95_000,  68_000,  88_000, 0.5,  "500ml", VariantStatus.ACTIVE);
        ProductVariant pv5b = pv(p5, "IODIN10-1L",    "8935005001012", 175_000, 128_000, 162_000, 1.0,  "1L",    VariantStatus.ACTIVE);
        skua(pv5a, attrW, av500g);  skua(pv5a, attrP, avBottle);
        skua(pv5b, attrW, av1kg);   skua(pv5b, attrP, avBottle);

        // P6: Khoáng tổng hợp AQUAMIN
        Product p6  = prod("Khoáng Tổng Hợp AQUAMIN", "khoang-tong-hop-aquamin",
            "Khoáng đa vi lượng Ca, Mg, K, Na bổ sung cho tôm lột xác",
            "AQUAMIN cung cấp đầy đủ khoáng chất cần thiết giúp tôm cứng vỏ sau lột, giảm hiện tượng mềm vỏ, tăng tỷ lệ sống. Sử dụng định kỳ hoặc sau khi mưa lớn.",
            bUni, catMin, "Thái Lan", "AQUAMIN");
        ProductVariant pv6a = pv(p6, "AQUAMIN-1KG", "8935006001011", 120_000,  88_000, 112_000, 1.0, "1kg", VariantStatus.ACTIVE);
        ProductVariant pv6b = pv(p6, "AQUAMIN-5KG", "8935006001012", 550_000, 405_000, 515_000, 5.0, "5kg", VariantStatus.ACTIVE);
        skua(pv6a, attrW, av1kg);   skua(pv6a, attrP, avBag);
        skua(pv6b, attrW, av5kg);   skua(pv6b, attrP, avBag);

        // P7: Vitamin C Ascorbic 35%
        Product p7  = prod("Vitamin C Ascorbic 35%", "vitamin-c-ascorbic-35",
            "Vitamin C dạng bột ổn định 35%, tăng sức đề kháng cho tôm cá",
            "Vitamin C Stay-C 35% bền với nhiệt độ pellet hóa, không bị phân hủy trong quá trình chế biến. Bổ sung vào thức ăn giúp tôm tăng miễn dịch, chống stress, phục hồi nhanh sau bệnh.",
            bGrobest, catVit, "Thái Lan", "VITC35");
        ProductVariant pv7a = pv(p7, "VITC35-500G", "8935007001011",  72_000,  52_000,  66_000, 0.5, "500g", VariantStatus.ACTIVE);
        ProductVariant pv7b = pv(p7, "VITC35-1KG",  "8935007001012", 135_000,  98_000, 125_000, 1.0, "1kg",  VariantStatus.ACTIVE);
        skua(pv7a, attrW, av500g);  skua(pv7a, attrP, avBag);
        skua(pv7b, attrW, av1kg);   skua(pv7b, attrP, avBag);

        // P8: Vôi CaO xử lý đáy ao
        Product p8  = prod("Vôi CaO Xử Lý Đáy Ao", "voi-cao-xu-ly-day-ao",
            "Vôi nung CaO độ tinh khiết ≥90%, cải tạo pH đáy ao",
            "Vôi CaO Green Nature được sản xuất từ đá vôi nguyên chất, hàm lượng CaO ≥90%. Dùng rải đều đáy ao trước vụ nuôi để nâng pH, diệt khuẩn, khử phèn hiệu quả.",
            bGN, catPondE, "Việt Nam", "VNCAO");
        ProductVariant pv8a = pv(p8, "VNCAO-10KG", "8935008001011",  45_000,  32_000,  40_000, 10.0, "10kg", VariantStatus.ACTIVE);
        ProductVariant pv8b = pv(p8, "VNCAO-25KG", "8935008001012",  95_000,  68_000,  85_000, 25.0, "25kg", VariantStatus.ACTIVE);
        skua(pv8a, attrW, av10kg);  skua(pv8a, attrP, avSack);
        skua(pv8b, attrW, av25kg);  skua(pv8b, attrP, avSack);

        // P9: Khúc xạ kế đo độ mặn
        Product p9  = prod("Khúc Xạ Kế Đo Độ Mặn 0-100‰", "khuc-xa-ke-do-do-man-0-100",
            "Khúc xạ kế đo độ mặn dải 0-100‰, chống thấm nước IPX4",
            "Khúc xạ kế chuyên dụng cho ao tôm với thang đo 0-100ppt (‰), độ chính xác ±1‰, có bù nhiệt độ tự động ATC. Thân nhôm hợp kim chịu va đập, chống thấm nước.",
            bGN, catMeas, "Trung Quốc", "SALT-REF");
        ProductVariant pv9a = pv(p9, "SALT-REF-01", "8935009001011", 320_000, 240_000, 300_000, 0.2, "1 cái", VariantStatus.ACTIVE);

        // P10: Bacillus Subtilis xử lý đáy ao
        Product p10 = prod("Bacillus Subtilis Xử Lý Đáy Ao", "bacillus-subtilis-xu-ly-day-ao",
            "Bào tử Bacillus subtilis ≥10⁹ CFU/g, phân giải chất hữu cơ đáy ao",
            "Chế phẩm Bacillus subtilis dạng bột, mật độ bào tử ≥10⁹ CFU/g. Phân giải mùn bã, giảm khí độc H₂S và NH₃, ức chế vi khuẩn gây bệnh. Dùng định kỳ 5-7 ngày/lần.",
            bGN, catPro, "Việt Nam", "BAC-SUB");
        ProductVariant pv10a = pv(p10, "BACSUB-1KG", "8935010001011", 195_000, 145_000, 180_000, 1.0, "1kg", VariantStatus.ACTIVE);
        ProductVariant pv10b = pv(p10, "BACSUB-5KG", "8935010001012", 920_000, 685_000, 860_000, 5.0, "5kg", VariantStatus.ACTIVE);
        skua(pv10a, attrW, av1kg);  skua(pv10a, attrP, avBag);
        skua(pv10b, attrW, av5kg);  skua(pv10b, attrP, avSack);

        // ── 9. NHÀ CUNG CẤP ─────────────────────────────────────────────
        Supplier supCP  = supplierRepository.save(Supplier.builder()
            .code("NCC-001").name("Công Ty TNHH CP Vietnam").taxCode("0101234567").category(catSF)
            .contactName("Nguyễn Văn Hùng").phone("0901999101").email("supply@cpvietnam.com.vn")
            .provinceId("79").addressDetail("KCN Mỹ Phước, Bình Dương")
            .paymentTerm(PaymentTerm.NET30).creditLimit(bd(500_000_000)).discount(5.0).currentDebt(BigDecimal.ZERO)
            .bankName("VPBank").bankAccountNumber("0521000123456").bankAccountHolder("Công ty TNHH CP Vietnam")
            .status(SupplierStatus.ACTIVE).note("Nhà cung cấp thức ăn tôm lớn nhất khu vực ĐBSCL").build());

        Supplier supGN  = supplierRepository.save(Supplier.builder()
            .code("NCC-002").name("Công Ty TNHH Green Nature Việt Nam").taxCode("0201234567").category(catPro)
            .contactName("Trần Thị Lan").phone("0902999202").email("sales@greennature.vn")
            .provinceId("79").addressDetail("Lô B12, KCN Trà Nóc 2, Cần Thơ")
            .paymentTerm(PaymentTerm.NET15).creditLimit(bd(200_000_000)).discount(3.0).currentDebt(BigDecimal.ZERO)
            .bankName("Agribank").bankAccountNumber("0310000654321").bankAccountHolder("Công ty Green Nature VN")
            .status(SupplierStatus.ACTIVE).note("Chuyên chế phẩm sinh học và vôi xử lý ao").build());

        Supplier supGrobest = supplierRepository.save(Supplier.builder()
            .code("NCC-003").name("Công Ty Cổ Phần Grobest Việt Nam").taxCode("0301234567").category(catMed)
            .contactName("Lê Văn Minh").phone("0903999303").email("vn@grobest.com")
            .provinceId("79").addressDetail("Số 8 Hùng Vương, Q.Bình Thủy, Cần Thơ")
            .paymentTerm(PaymentTerm.NET30).creditLimit(bd(300_000_000)).discount(4.0).currentDebt(BigDecimal.ZERO)
            .bankName("Techcombank").bankAccountNumber("0108000789012").bankAccountHolder("Grobest Vietnam JSC")
            .status(SupplierStatus.ACTIVE).note("Thuốc thủy sản và vitamin nhập khẩu Đài Loan/Thái Lan").build());

        Supplier supBM  = supplierRepository.save(Supplier.builder()
            .code("NCC-004").name("Đại Lý Thức Ăn Thủy Sản Bình Minh").taxCode(null).category(catFF)
            .contactName("Phạm Văn Bình").phone("0904999404").email("binhminhts@gmail.com")
            .provinceId("94").addressDetail("Khóm 3, P.7, TP.Sóc Trăng, Sóc Trăng")
            .paymentTerm(PaymentTerm.IMMEDIATE).creditLimit(bd(50_000_000)).discount(2.0).currentDebt(BigDecimal.ZERO)
            .bankName("Vietcombank").bankAccountNumber("0070010111222").bankAccountHolder("Pham Van Binh")
            .status(SupplierStatus.ACTIVE).note("Đại lý cấp 2 khu vực Sóc Trăng").build());

        // ── 10. TỒN KHO ────────────────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        // main warehouse stock
        inv(pv1a, mainWh, 120, 20, now); inv(pv1b, mainWh,  40, 10, now);
        inv(pv2a, mainWh,  95, 20, now); inv(pv2b, mainWh,  30, 10, now);
        inv(pv3a, mainWh,  80, 15, now); inv(pv3b, mainWh,  25,  8, now);
        inv(pv4a, mainWh, 200, 30, now); inv(pv4b, mainWh, 150, 20, now);
        inv(pv5a, mainWh, 180, 25, now); inv(pv5b, mainWh, 120, 15, now);
        inv(pv6a, mainWh, 160, 20, now); inv(pv6b, mainWh,  90, 10, now);
        inv(pv7a, mainWh, 300, 50, now); inv(pv7b, mainWh, 200, 30, now);
        inv(pv8a, mainWh, 500, 80, now); inv(pv8b, mainWh, 250, 40, now);
        inv(pv9a, mainWh,  50,  5, now);
        inv(pv10a,mainWh,  80, 10, now); inv(pv10b,mainWh,  30,  5, now);
        // branch 1 stock
        inv(pv1a, branch1,  30, 10, now); inv(pv1b, branch1,  10,  5, now);
        inv(pv2a, branch1,  25,  8, now); inv(pv2b, branch1,   8,  3, now);
        inv(pv3a, branch1,  20,  5, now); inv(pv3b, branch1,   6,  2, now);
        inv(pv4a, branch1,  50, 10, now); inv(pv4b, branch1,  40,  8, now);
        inv(pv5a, branch1,  45,  8, now); inv(pv5b, branch1,  30,  5, now);
        inv(pv6a, branch1,  40,  8, now); inv(pv6b, branch1,  20,  5, now);
        inv(pv7a, branch1,  80, 15, now); inv(pv7b, branch1,  60, 10, now);
        inv(pv8a, branch1, 120, 20, now); inv(pv8b, branch1,  60, 10, now);
        inv(pv9a, branch1,  10,  2, now);
        inv(pv10a,branch1,  20,  5, now); inv(pv10b,branch1,   8,  2, now);
        // branch 2 stock
        inv(pv1a, branch2,  20,  5, now); inv(pv1b, branch2,   6,  2, now);
        inv(pv2a, branch2,  18,  5, now); inv(pv2b, branch2,   5,  2, now);
        inv(pv4a, branch2,  40,  8, now); inv(pv4b, branch2,  30,  5, now);
        inv(pv5a, branch2,  35,  5, now); inv(pv5b, branch2,  25,  4, now);
        inv(pv6a, branch2,  30,  5, now); inv(pv8a, branch2,  90, 15, now);
        inv(pv10a,branch2,  15,  3, now);

        // ── 11. PHIẾU NHẬP HÀNG ────────────────────────────────────────
        InventoryNote n1 = inventoryNoteRepository.save(InventoryNote.builder()
            .code("PN-2024-001").type(InventoryNoteType.IMPORT).branch(mainWh).supplier(supCP)
            .createdBy(admin).reason("Nhập hàng định kỳ tháng 10 từ CP Vietnam")
            .status(InventoryNoteStatus.COMPLETED).totalAmount(bd(9_560_000))
            .paymentAmount(bd(9_560_000)).debtAmount(BigDecimal.ZERO)
            .deliverer("Tài xế: Nguyễn Hữu Tài - Xe 72C-12345")
            .createdAt(now.minusDays(30)).entryDate(now.minusDays(30)).build());
        nd(n1, pv1a, 20, bd(140_000), "LOT-CP-2410-01", now.plusMonths(12));
        nd(n1, pv1b, 10, bd(660_000), "LOT-CP-2410-01", now.plusMonths(12));
        nd(n1, pv2a, 15, bd(128_000), "LOT-TOM-2410-01", now.plusMonths(10));
        nd(n1, pv2b,  5, bd(605_000), "LOT-TOM-2410-01", now.plusMonths(10));

        InventoryNote n2 = inventoryNoteRepository.save(InventoryNote.builder()
            .code("PN-2024-002").type(InventoryNoteType.IMPORT).branch(mainWh).supplier(supGN)
            .createdBy(kho1).reason("Nhập chế phẩm vi sinh và vôi CaO tháng 10")
            .status(InventoryNoteStatus.COMPLETED).totalAmount(bd(5_830_000))
            .paymentAmount(bd(5_830_000)).debtAmount(BigDecimal.ZERO)
            .deliverer("Tài xế: Trần Văn Giao - Xe 72C-67890")
            .createdAt(now.minusDays(20)).entryDate(now.minusDays(20)).build());
        nd(n2, pv4a,  50, bd(62_000),  "LOT-EM-2410-01",  now.plusMonths(18));
        nd(n2, pv4b,  30, bd(112_000), "LOT-EM-2410-01",  now.plusMonths(18));
        nd(n2, pv8a,  30, bd(32_000),  "LOT-CAO-2410-01", now.plusMonths(36));
        nd(n2, pv8b,  10, bd(68_000),  "LOT-CAO-2410-01", now.plusMonths(36));
        nd(n2, pv10a, 20, bd(145_000), "LOT-BAC-2410-01", now.plusMonths(24));
        nd(n2, pv10b,  5, bd(685_000), "LOT-BAC-2410-01", now.plusMonths(24));

        InventoryNote n3 = inventoryNoteRepository.save(InventoryNote.builder()
            .code("PN-2024-003").type(InventoryNoteType.IMPORT).branch(mainWh).supplier(supGrobest)
            .createdBy(kho1).reason("Nhập thuốc và vitamin định kỳ tháng 11")
            .status(InventoryNoteStatus.PENDING).totalAmount(bd(5_695_000))
            .paymentAmount(BigDecimal.ZERO).debtAmount(bd(5_695_000))
            .createdAt(now.minusDays(2)).entryDate(null).build());
        nd(n3, pv5a, 40, bd(68_000),  "LOT-IOD-2411-01", now.plusMonths(24));
        nd(n3, pv5b, 20, bd(128_000), "LOT-IOD-2411-01", now.plusMonths(24));
        nd(n3, pv7a, 30, bd(52_000),  "LOT-VTC-2411-01", now.plusMonths(24));
        nd(n3, pv7b, 15, bd(98_000),  "LOT-VTC-2411-01", now.plusMonths(24));
        nd(n3, pv6a, 20, bd(88_000),  "LOT-AQM-2411-01", now.plusMonths(24));
        nd(n3, pv6b,  5, bd(405_000), "LOT-AQM-2411-01", now.plusMonths(24));

        InventoryNote n4 = inventoryNoteRepository.save(InventoryNote.builder()
            .code("PN-2024-004").type(InventoryNoteType.IMPORT).branch(branch2).supplier(supBM)
            .createdBy(kho2).reason("Chi nhánh Sóc Trăng nhập thức ăn cá tháng 10")
            .status(InventoryNoteStatus.COMPLETED).totalAmount(bd(3_690_000))
            .paymentAmount(bd(3_690_000)).debtAmount(BigDecimal.ZERO)
            .deliverer("Giao hàng tận nơi từ đại lý Bình Minh")
            .createdAt(now.minusDays(15)).entryDate(now.minusDays(15)).build());
        nd(n4, pv3a, 15, bd(108_000), "LOT-UNI-2410-01", now.plusMonths(8));
        nd(n4, pv3b,  5, bd(510_000), "LOT-UNI-2410-01", now.plusMonths(8));

        // ── 12. KHÁCH HÀNG ──────────────────────────────────────────────
        customerRepository.save(Customer.builder()
            .user(user1).name("Nguyễn Văn Tôm").phone("0911000001").email("user1@gmail.com")
            .gender(CustomerGender.MALE).provinceId("79")
            .addressDetail("123 Ấp 5, Xã Tân Duyệt, H.Đầm Dơi, Cà Mau")
            .status(CustomerStatus.ACTIVE).note("Hộ nuôi tôm 3ha tại Cà Mau, mua định kỳ hàng tháng").build());
        customerRepository.save(Customer.builder()
            .user(user2).name("Trần Thị Cua").phone("0911000002").email("user2@gmail.com")
            .gender(CustomerGender.FEMALE).provinceId("94")
            .addressDetail("45 Đường Số 6, KCN An Nghiệp, TP.Sóc Trăng")
            .status(CustomerStatus.ACTIVE).note("Nuôi cua biển kết hợp tôm sú tại Sóc Trăng").build());
        customerRepository.save(Customer.builder()
            .user(user3).name("Lê Minh Nuôi").phone("0911000003").email("user3@gmail.com")
            .gender(CustomerGender.MALE).provinceId("95")
            .addressDetail("78 Ấp Bình Hòa, Xã Vĩnh Hậu A, H.Hòa Bình, Bạc Liêu")
            .status(CustomerStatus.ACTIVE).note("Hộ nuôi thủy sản 5ha tại Bạc Liêu").build());

        // ── 13. ĐƠN HÀNG ────────────────────────────────────────────────
        // Đơn 1 – COMPLETED, đã thanh toán
        Order o1 = orderRepository.save(Order.builder()
            .code("DH-2024-001").branch(branch1).user(user1).status(OrderStatus.COMPLETED)
            .paymentMethod(PaymentMethod.TRANSFER).paymentStatus(PaymentStatus.PAID)
            .shippingAddress("123 Ấp 5, Xã Tân Duyệt, H.Đầm Dơi, Cà Mau")
            .totalAmount(bd(1_055_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(1_055_000))
            .createdAt(now.minusDays(15)).build());
        orderItemRepository.save(oi(o1, pv1a, 3, bd(180_000)));
        orderItemRepository.save(oi(o1, pv4a, 5, bd(85_000)));
        orderItemRepository.save(oi(o1, pv8a, 2, bd(45_000)));

        // Đơn 2 – SHIPPING, COD chưa thanh toán
        Order o2 = orderRepository.save(Order.builder()
            .code("DH-2024-002").branch(branch1).user(user2).status(OrderStatus.SHIPPING)
            .paymentMethod(PaymentMethod.COD).paymentStatus(PaymentStatus.UNPAID)
            .shippingAddress("45 Đường Số 6, KCN An Nghiệp, TP.Sóc Trăng")
            .totalAmount(bd(1_020_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(1_020_000))
            .createdAt(now.minusDays(5)).build());
        orderItemRepository.save(oi(o2, pv2a, 4, bd(165_000)));
        orderItemRepository.save(oi(o2, pv6a, 3, bd(120_000)));

        // Đơn 3 – PENDING, chưa xác nhận
        Order o3 = orderRepository.save(Order.builder()
            .code("DH-2024-003").branch(mainWh).user(user3).status(OrderStatus.PENDING)
            .paymentMethod(PaymentMethod.CASH).paymentStatus(PaymentStatus.UNPAID)
            .shippingAddress("78 Ấp Bình Hòa, Xã Vĩnh Hậu A, H.Hòa Bình, Bạc Liêu")
            .totalAmount(bd(1_256_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(1_256_000))
            .createdAt(now.minusDays(1)).build());
        orderItemRepository.save(oi(o3, pv1b, 1, bd(850_000)));
        orderItemRepository.save(oi(o3, pv5a, 2, bd(95_000)));
        orderItemRepository.save(oi(o3, pv7a, 3, bd(72_000)));

        // Đơn 4 – CONFIRMED, đã thanh toán chuyển khoản
        Order o4 = orderRepository.save(Order.builder()
            .code("DH-2024-004").branch(branch2).user(user1).status(OrderStatus.CONFIRMED)
            .paymentMethod(PaymentMethod.TRANSFER).paymentStatus(PaymentStatus.PAID)
            .shippingAddress("123 Ấp 5, Xã Tân Duyệt, H.Đầm Dơi, Cà Mau")
            .totalAmount(bd(1_090_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(1_090_000))
            .createdAt(now.minusDays(3)).build());
        orderItemRepository.save(oi(o4, pv2b, 1, bd(780_000)));
        orderItemRepository.save(oi(o4, pv4b, 2, bd(155_000)));

        // Đơn 5 – CANCELLED
        Order o5 = orderRepository.save(Order.builder()
            .code("DH-2024-005").branch(branch1).user(user2).status(OrderStatus.CANCELLED)
            .paymentMethod(PaymentMethod.CASH).paymentStatus(PaymentStatus.UNPAID)
            .shippingAddress("45 Đường Số 6, KCN An Nghiệp, TP.Sóc Trăng")
            .totalAmount(bd(280_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(280_000))
            .createdAt(now.minusDays(10)).build());
        orderItemRepository.save(oi(o5, pv3a, 2, bd(140_000)));

        // ── 14. ĐIỀU CHUYỂN HÀNG ─────────────────────────────────────
        inventoryTransferRepository.save(InventoryTransfer.builder()
            .transferCode("DC-2024-001").fromBranch(mainWh).toBranch(branch1)
            .sender(kho1).receiver(kho2).status(InventoryTransferStatus.COMPLETED)
            .transferType("BETWEEN_WAREHOUSES").vehicle("Xe tải 72C-12345").transporter("Nguyễn Hữu Tài")
            .dispatchOrder("DC-HIGH-2024-001").referenceCode("YCBS-2024-002").priority("HIGH")
            .description("Điều chuyển hàng định kỳ bổ sung cho Chi nhánh Cần Thơ")
            .transferDate(now.minusDays(25)).deadline(now.minusDays(23))
            .totalValue(bd(3_850_000)).totalQuantity(80).createdAt(now.minusDays(26)).build());

        inventoryTransferRepository.save(InventoryTransfer.builder()
            .transferCode("DC-2024-002").fromBranch(mainWh).toBranch(branch2)
            .sender(kho1).receiver(kho2).status(InventoryTransferStatus.SHIPPING)
            .transferType("BETWEEN_WAREHOUSES").vehicle("Xe tải 72C-67890").transporter("Trần Văn Giao")
            .dispatchOrder("DC-NORM-2024-002").referenceCode("YCBS-2024-003").priority("NORMAL")
            .description("Điều chuyển hàng bổ sung cho Chi nhánh Sóc Trăng tháng 11")
            .transferDate(now.minusDays(1)).deadline(now.plusDays(2))
            .totalValue(bd(2_540_000)).totalQuantity(55).createdAt(now.minusDays(2)).build());

        log.info(">>> KHỞI TẠO DỮ LIỆU DEMO HOÀN TẤT.");
        log.info(">>> Tài khoản: admin@agrishrimp.vn / kho1@agrishrimp.vn / sales1@agrishrimp.vn (mật khẩu: 123456)");
        log.info(">>> Khách hàng: user1@gmail.com / user2@gmail.com / user3@gmail.com (mật khẩu: 123456)");
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
                .description("Vai trò " + displayName + " trong hệ thống AgriShrimp")
                .permissions(perms).build()));
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

    private ProductVariant pv(Product product, String sku, String barcode,
                               double price, double importPrice, double wholesalePrice,
                               double weight, String unit, VariantStatus status) {
        return productVariantRepository.findBySku(sku).orElseGet(() ->
            productVariantRepository.save(ProductVariant.builder()
                .sku(sku).barcode(barcode).price(bd(price)).importPrice(bd(importPrice))
                .wholesalePrice(bd(wholesalePrice)).quantity(0)
                .shippingWeight(bd(weight)).status(status).product(product).build()));
    }

    private void skua(ProductVariant variant, Attribute attr, AttributeValue attrVal) {
        skuAttributeValueRepository.save(
            SKUAttributeValue.builder().sku(variant).attribute(attr).attributeValue(attrVal).build());
    }

    private void inv(ProductVariant variant, Branch branch, int qty, int minStock, LocalDateTime now) {
        inventoryRepository.findByBranchAndProductVariant(branch, variant).orElseGet(() ->
            inventoryRepository.save(Inventory.builder()
                .productVariant(variant).branch(branch).quantity(qty)
                .lastReceiptDate(now).build()));
    }

    private void nd(InventoryNote note, ProductVariant variant, int qty, BigDecimal price,
                    String batch, LocalDateTime expiry) {
        inventoryNoteDetailRepository.save(InventoryNoteDetail.builder()
            .inventoryNote(note).productVariant(variant)
            .quantity(qty).price(price).batchNumber(batch).expiryDate(expiry).build());
    }

    private OrderItem oi(Order order, ProductVariant variant, int qty, BigDecimal price) {
        return OrderItem.builder().order(order).productVariant(variant)
            .quantity(qty).price(price).build();
    }

    private BigDecimal bd(double val) {
        return BigDecimal.valueOf(val);
    }
}

package com.zone.agri.config;

import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.*;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
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
        if (branchRepository.count() > 0) {
            log.info(">>> Dữ liệu demo đã được khởi tạo. Bỏ qua...");
            return;
        }
        log.info(">>> BẮT ĐẦU KHỞI TẠO DỮ LIỆU DEMO (MÔ HÌNH LÔ HÀNG ĐỘNG)...");

        Role adminRole   = roleRepository.findBySlug("ADMIN").orElseThrow();
        Role managerRole = roleRepository.findBySlug("MANAGER").orElseThrow();
        Role staffRole   = roleRepository.findBySlug("STAFF").orElseThrow();
        Role userRole    = roleRepository.findBySlug("USER").orElseThrow();

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
        User kho3  = saveUser("Phạm Văn Bạc Liêu",  "kho3@agrishrimp.vn",   "0901000004", "123456", managerRole, branch3,  Gender.MALE,   LocalDate.of(1994,  3, 10));
        User kho4  = saveUser("Nguyễn Thị Cà Mau",  "kho4@agrishrimp.vn",   "0901000005", "123456", managerRole, branch4,  Gender.FEMALE, LocalDate.of(1991,  8, 22));
        User kho5  = saveUser("Lê Văn Kiên Giang",  "kho5@agrishrimp.vn",   "0901000006", "123456", staffRole,   branch5,  Gender.MALE,   LocalDate.of(1996,  5, 15));
        User kho6  = saveUser("Trần Thị An Giang",  "kho6@agrishrimp.vn",   "0901000007", "123456", staffRole,   branch6,  Gender.FEMALE, LocalDate.of(1993, 12,  3));
        User kho7  = saveUser("Võ Văn Bến Tre",     "kho7@agrishrimp.vn",   "0901000008", "123456", staffRole,   branch7,  Gender.MALE,   LocalDate.of(1995,  7, 18));
        User kho8  = saveUser("Huỳnh Thị Trà Vinh", "kho8@agrishrimp.vn",   "0901000009", "123456", staffRole,   branch8,  Gender.FEMALE, LocalDate.of(1992,  4, 30));
        User kho9  = saveUser("Dương Văn Tiền Giang","kho9@agrishrimp.vn",   "0901000010", "123456", staffRole,   branch9,  Gender.MALE,   LocalDate.of(1994,  9, 12));
        User kho10 = saveUser("Bùi Thị Long An",    "kho10@agrishrimp.vn",  "0901000011", "123456", staffRole,   branch10, Gender.FEMALE, LocalDate.of(1997,  1, 25));
        User kho11 = saveUser("Phan Văn Vĩnh Long", "kho11@agrishrimp.vn",  "0901000012", "123456", staffRole,   branch11, Gender.MALE,   LocalDate.of(1993,  6,  8));
        User kho12 = saveUser("Ngô Thị Đồng Tháp",  "kho12@agrishrimp.vn",  "0901000013", "123456", staffRole,   branch12, Gender.FEMALE, LocalDate.of(1996, 11, 17));

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
        AttributeValue avBox    = av(attrP, "Hộp giấy");
        AttributeValue av1kg    = av(attrW, "1kg");
        AttributeValue av10kg   = av(attrW, "10kg");
        AttributeValue av50kg   = av(attrW, "50kg");

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

        Product p2  = prod("Thức Ăn Tôm Tomboy #1 Ươm Giống", "thuc-an-tom-tomboy-1",
                "Thức ăn micro-pellet ươm giống tôm thẻ chân trắng", "Tomboy #1 giàu protein 42%, lipid 8%...", bTomboy, catSF, "Việt Nam", "TBY-1");
        ProductVariant pv2a = pv(p2, "TBY-1-500G", "8935002001001", VariantStatus.ACTIVE);
        ProductVariant pv2b = pv(p2, "TBY-1-5KG",  "8935002001002", VariantStatus.ACTIVE);
        skua(pv2a, attrW, av500g); skua(pv2a, attrP, avBag);
        skua(pv2b, attrW, av5kg);  skua(pv2b, attrP, avBag);

        Product p3  = prod("Khoáng Tổng Hợp EDTA Agri-Min", "khoang-edta-tong-hop",
                "Bổ sung khoáng đa vi lượng Ca, Mg, K, Na cho ao tôm", "Khoáng EDTA hỗn hợp Ca-Mg-K-Na dạng bột tan nhanh...", bGN, catMin, "Việt Nam", "GN-EDTA");
        ProductVariant pv3a = pv(p3, "GN-EDTA-1KG", "8935003001001", VariantStatus.ACTIVE);
        ProductVariant pv3b = pv(p3, "GN-EDTA-5KG", "8935003001002", VariantStatus.ACTIVE);
        skua(pv3a, attrW, av1kg);  skua(pv3a, attrP, avBag);
        skua(pv3b, attrW, av5kg);  skua(pv3b, attrP, avSack);

        Product p5  = prod("Vitamin C Aqua Stable", "vitamin-c-aqua-stable",
                "Vitamin C bền vững trong nước, tăng sức đề kháng tôm", "Vitamin C 35% dạng bột, bền pH 5-8, không bị oxy hóa nhanh...", bGN, catVit, "Việt Nam", "GN-VTC");
        ProductVariant pv5a = pv(p5, "GN-VTC-500G", "8935005001001", VariantStatus.ACTIVE);
        ProductVariant pv5b = pv(p5, "GN-VTC-5KG",  "8935005001002", VariantStatus.ACTIVE);
        skua(pv5a, attrW, av500g); skua(pv5a, attrP, avBag);
        skua(pv5b, attrW, av5kg);  skua(pv5b, attrP, avSack);

        Product p6  = prod("Vôi Dolomite Cân Bằng pH Ao", "voi-dolomite-xu-ly-ao",
                "Cân bằng pH đáy ao, kiềm hóa môi trường nuôi tôm", "Vôi dolomite CaMg(CO3)2 nghiền mịn, độ tinh khiết >95%...", bCP, catMin, "Việt Nam", "CP-DOLO");
        ProductVariant pv6a = pv(p6, "CP-DOLO-25KG", "8935006001001", VariantStatus.ACTIVE);
        ProductVariant pv6b = pv(p6, "CP-DOLO-50KG", "8935006001002", VariantStatus.ACTIVE);
        skua(pv6a, attrW, av25kg); skua(pv6a, attrP, avSack);
        skua(pv6b, attrW, av50kg); skua(pv6b, attrP, avSack);

        Product p7  = prod("Bacillus BioPlus Vi Sinh Đáy Ao", "bacillus-bioplus",
                "Vi sinh phân giải khí độc H2S, NH3 tích tụ đáy ao", "Bacillus subtilis mật độ 10^9 CFU/g, Bacillus licheniformis...", bGN, catPro, "Việt Nam", "GN-BIO");
        ProductVariant pv7a = pv(p7, "GN-BIO-500G", "8935007001001", VariantStatus.ACTIVE);
        ProductVariant pv7b = pv(p7, "GN-BIO-5KG",  "8935007001002", VariantStatus.ACTIVE);
        skua(pv7a, attrW, av500g); skua(pv7a, attrP, avBottle);
        skua(pv7b, attrW, av5kg);  skua(pv7b, attrP, avSack);

        Product p8  = prod("OTC-80 Oxytetracycline Kháng Khuẩn", "otc-80-oxytetracycline",
                "Kháng khuẩn phổ rộng cho tôm cá bị đốm trắng, hoại tử", "Oxytetracycline HCl 80% dạng bột tan nước, phổ kháng khuẩn rộng...", bCP, catMed, "Việt Nam", "CP-OTC");
        ProductVariant pv8a = pv(p8, "CP-OTC-500G", "8935008001001", VariantStatus.ACTIVE);
        ProductVariant pv8b = pv(p8, "CP-OTC-1KG",  "8935008001002", VariantStatus.ACTIVE);
        skua(pv8a, attrW, av500g); skua(pv8a, attrP, avBox);
        skua(pv8b, attrW, av1kg);  skua(pv8b, attrP, avBox);

        Product p9  = prod("Thức Ăn Cá Da Trơn Tomboy 26%", "thuc-an-ca-da-tron-tomboy-26",
                "Thức ăn viên nổi cho cá tra, cá basa, protein 26%", "Tomboy 26% protein, lipid 5%, dạng viên nổi 2.5mm...", bTomboy, catFF, "Việt Nam", "TBY-CAT");
        ProductVariant pv9a = pv(p9, "TBY-CAT-5KG",  "8935009001001", VariantStatus.ACTIVE);
        ProductVariant pv9b = pv(p9, "TBY-CAT-25KG", "8935009001002", VariantStatus.ACTIVE);
        skua(pv9a, attrW, av5kg);  skua(pv9a, attrP, avBag);
        skua(pv9b, attrW, av25kg); skua(pv9b, attrP, avSack);

        Product p10 = prod("Máy Đo pH-DO-Nhiệt Độ Cầm Tay", "may-do-ph-do-nhiet-do",
                "Thiết bị đo đa chỉ tiêu: pH, oxy hòa tan, nhiệt độ ao nuôi", "Độ chính xác pH ±0.01, DO ±0.1 mg/L, Temp ±0.5°C, chống nước IP67...", bGN, catMeas, "Nhật Bản", "GN-METER");
        ProductVariant pv10a = pv(p10, "GN-METER-01", "8935010001001", VariantStatus.ACTIVE);

        // ── 9. NHÀ CUNG CẤP ─────────────────────────────────────────────
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

        // Thêm tồn kho sản phẩm mới cho kho tổng và chi nhánh cũ
        inv(pv2b,  mainWh,  150, bd(130_000),   "LOT-MAIN-TBY-001",   20, now);
        inv(pv3b,  mainWh,  100, bd(380_000),   "LOT-MAIN-MIN-001",   10, now);
        inv(pv5b,  mainWh,   80, bd(330_000),   "LOT-MAIN-VIT-001",   10, now);
        inv(pv6a,  mainWh,  200, bd(85_000),    "LOT-MAIN-VOIA-001",  20, now);
        inv(pv7b,  mainWh,   80, bd(950_000),   "LOT-MAIN-BIO-001",   10, now);
        inv(pv8a,  mainWh,   50, bd(280_000),   "LOT-MAIN-OTC-001",    5, now);
        inv(pv9b,  mainWh,   80, bd(550_000),   "LOT-MAIN-CAT-001",   10, now);
        inv(pv10a, mainWh,    5, bd(3_500_000), "LOT-MAIN-METER-001",  1, now);

        inv(pv2b,  branch1, 80, bd(130_000), "LOT-CN1-TBY-001",  10, now);
        inv(pv3a,  branch1, 60, bd(85_000),  "LOT-CN1-MIN-001",  10, now);
        inv(pv5a,  branch1,100, bd(75_000),  "LOT-CN1-VIT-001",  15, now);
        inv(pv6a,  branch1, 50, bd(85_000),  "LOT-CN1-VOIA-001", 10, now);
        inv(pv7a,  branch1, 40, bd(220_000), "LOT-CN1-BIO-001",  10, now);
        inv(pv2a,  branch1, 60, bd(35_000),  "LOT-CN1-TBY-500G-001", 10, now);

        inv(pv2a,  branch2, 40, bd(35_000),  "LOT-CN2-TBY-500G-001", 10, now);
        inv(pv2b,  branch2, 40, bd(130_000), "LOT-CN2-TBY-001",  10, now);
        inv(pv5b,  branch2, 50, bd(330_000), "LOT-CN2-VIT-001",  10, now);
        inv(pv6a,  branch2, 80, bd(85_000),  "LOT-CN2-VOIA-001", 10, now);

        // Tồn kho cho 10 chi nhánh mới (từ phiếu nhập COMPLETED)
        inv(pv1a,  branch3, 120, bd(145_000), "LOT-CN3-CP-001",   15, now);
        inv(pv1b,  branch3,  60, bd(660_000), "LOT-CN3-CP-001",    5, now);
        inv(pv4a,  branch3,  80, bd(62_000),  "LOT-CN3-EM-001",   10, now);
        inv(pv7a,  branch3,  50, bd(220_000), "LOT-CN3-BIO-001",  10, now);

        inv(pv1a,  branch4, 200, bd(145_000), "LOT-CN4-CP-001",   20, now);
        inv(pv1b,  branch4, 100, bd(660_000), "LOT-CN4-CP-001",   10, now);
        inv(pv2b,  branch4, 100, bd(130_000), "LOT-CN4-TBY-001",  15, now);
        inv(pv4a,  branch4, 100, bd(62_000),  "LOT-CN4-EM-001",   15, now);

        inv(pv1a,  branch5, 100, bd(145_000), "LOT-CN5-CP-001",   15, now);
        inv(pv2b,  branch5,  50, bd(130_000), "LOT-CN5-TBY-001",  10, now);
        inv(pv4a,  branch5,  60, bd(62_000),  "LOT-CN5-EM-001",   10, now);
        inv(pv7a,  branch5,  80, bd(220_000), "LOT-CN5-BIO-001",  10, now);

        inv(pv9a,  branch6,  80, bd(120_000), "LOT-CN6-CAT-001",  15, now);
        inv(pv9b,  branch6,  40, bd(550_000), "LOT-CN6-CAT-001",   5, now);
        inv(pv3a,  branch6,  60, bd(85_000),  "LOT-CN6-MIN-001",  10, now);
        inv(pv5a,  branch6,  80, bd(75_000),  "LOT-CN6-VIT-001",  10, now);

        inv(pv1a,  branch7,  90, bd(145_000), "LOT-CN7-CP-001",   15, now);
        inv(pv1b,  branch7,  45, bd(660_000), "LOT-CN7-CP-001",    5, now);
        inv(pv4a,  branch7,  70, bd(62_000),  "LOT-CN7-EM-001",   10, now);

        inv(pv1a,  branch8,  80, bd(145_000), "LOT-CN8-CP-001",   10, now);
        inv(pv2b,  branch8,  40, bd(130_000), "LOT-CN8-TBY-001",  10, now);
        inv(pv4a,  branch8,  60, bd(62_000),  "LOT-CN8-EM-001",   10, now);
        inv(pv5a,  branch8,  60, bd(75_000),  "LOT-CN8-VIT-001",  10, now);

        inv(pv9a,  branch9,  70, bd(120_000), "LOT-CN9-CAT-001",  15, now);
        inv(pv9b,  branch9,  30, bd(550_000), "LOT-CN9-CAT-001",   5, now);
        inv(pv1a,  branch9,  80, bd(145_000), "LOT-CN9-CP-001",   15, now);
        inv(pv2a,  branch9,  50, bd(35_000),  "LOT-CN9-TBY-001",  20, now);

        inv(pv1a,  branch10, 100, bd(145_000), "LOT-CN10-CP-001",  15, now);
        inv(pv2b,  branch10,  50, bd(130_000), "LOT-CN10-TBY-001", 10, now);
        inv(pv3a,  branch10,  80, bd(85_000),  "LOT-CN10-MIN-001", 10, now);
        inv(pv4a,  branch10,  60, bd(62_000),  "LOT-CN10-EM-001",  10, now);

        inv(pv1a,  branch11,  90, bd(145_000), "LOT-CN11-CP-001",  15, now);
        inv(pv9a,  branch11,  60, bd(120_000), "LOT-CN11-CAT-001", 10, now);
        inv(pv4a,  branch11,  50, bd(62_000),  "LOT-CN11-EM-001",  10, now);
        inv(pv5a,  branch11,  70, bd(75_000),  "LOT-CN11-VIT-001", 10, now);

        inv(pv9a,  branch12, 120, bd(120_000), "LOT-CN12-CAT-001", 20, now);
        inv(pv9b,  branch12,  60, bd(550_000), "LOT-CN12-CAT-001",  5, now);
        inv(pv3a,  branch12,  80, bd(85_000),  "LOT-CN12-MIN-001", 10, now);
        inv(pv6a,  branch12,  60, bd(85_000),  "LOT-CN12-VOIA-001",10, now);

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

        // Chi Nhánh Sóc Trăng — 2 phiếu
        InventoryNote nCn2a = saveNote("PN-CN2-001", branch2, supCP,  kho1, now.minusDays(45), InventoryNoteStatus.COMPLETED, bd(31_420_000));
        addDetail(nCn2a, pv1a, 60, bd(142_000), "LOT-CN2-CP-001");
        addDetail(nCn2a, pv1b, 30, bd(660_000), "LOT-CN2-CP-001");
        addDetail(nCn2a, pv4a, 50, bd(62_000),  "LOT-CN2-EM-001");

        InventoryNote nCn2b = saveNote("PN-CN2-002", branch2, supGN,  kho1, now.minusDays(21), InventoryNoteStatus.COMPLETED, bd(28_500_000));
        addDetail(nCn2b, pv2b, 40, bd(130_000), "LOT-CN2-TBY-001");
        addDetail(nCn2b, pv5b, 50, bd(330_000), "LOT-CN2-VIT-001");
        addDetail(nCn2b, pv6a, 80, bd(85_000),  "LOT-CN2-VOIA-001");

        // Chi Nhánh Bạc Liêu — 2 phiếu
        InventoryNote nCn3a = saveNote("PN-CN3-001", branch3, supCP,  kho3, now.minusDays(50), InventoryNoteStatus.COMPLETED, bd(72_960_000));
        addDetail(nCn3a, pv1a, 120, bd(145_000), "LOT-CN3-CP-001");
        addDetail(nCn3a, pv1b,  60, bd(660_000), "LOT-CN3-CP-001");
        addDetail(nCn3a, pv4a,  80, bd(62_000),  "LOT-CN3-EM-001");
        addDetail(nCn3a, pv7a,  50, bd(220_000), "LOT-CN3-BIO-001");

        InventoryNote nCn3b = saveNote("PN-CN3-002", branch3, supGN,  kho3, now.minusDays(20), InventoryNoteStatus.PENDING,   bd(16_400_000));
        addDetail(nCn3b, pv2a,  60, bd(35_000),  "LOT-CN3-TBY-001");
        addDetail(nCn3b, pv3a,  80, bd(85_000),  "LOT-CN3-MIN-001");
        addDetail(nCn3b, pv5a, 100, bd(75_000),  "LOT-CN3-VIT-001");

        // Chi Nhánh Cà Mau — 2 phiếu (lớn nhất, tỉnh nuôi tôm số 1)
        InventoryNote nCn4a = saveNote("PN-CN4-001", branch4, supCP,  kho4, now.minusDays(58), InventoryNoteStatus.COMPLETED, bd(114_200_000));
        addDetail(nCn4a, pv1a, 200, bd(145_000), "LOT-CN4-CP-001");
        addDetail(nCn4a, pv1b, 100, bd(660_000), "LOT-CN4-CP-001");
        addDetail(nCn4a, pv2b, 100, bd(130_000), "LOT-CN4-TBY-001");
        addDetail(nCn4a, pv4a, 100, bd(62_000),  "LOT-CN4-EM-001");

        InventoryNote nCn4b = saveNote("PN-CN4-002", branch4, supGN,  kho4, now.minusDays(25), InventoryNoteStatus.PENDING,   bd(62_950_000));
        addDetail(nCn4b, pv3b,  80, bd(380_000), "LOT-CN4-MIN-001");
        addDetail(nCn4b, pv5b,  60, bd(330_000), "LOT-CN4-VIT-001");
        addDetail(nCn4b, pv6a, 150, bd(85_000),  "LOT-CN4-VOIA-001");

        // Chi Nhánh Kiên Giang — 2 phiếu
        InventoryNote nCn5a = saveNote("PN-CN5-001", branch5, supCP,  kho5, now.minusDays(48), InventoryNoteStatus.COMPLETED, bd(42_320_000));
        addDetail(nCn5a, pv1a, 100, bd(145_000), "LOT-CN5-CP-001");
        addDetail(nCn5a, pv2b,  50, bd(130_000), "LOT-CN5-TBY-001");
        addDetail(nCn5a, pv4a,  60, bd(62_000),  "LOT-CN5-EM-001");
        addDetail(nCn5a, pv7a,  80, bd(220_000), "LOT-CN5-BIO-001");

        InventoryNote nCn5b = saveNote("PN-CN5-002", branch5, supMW,  kho5, now.minusDays(18), InventoryNoteStatus.PENDING,   bd(24_800_000));
        addDetail(nCn5b, pv3a,  40, bd(85_000),  "LOT-CN5-MIN-001");
        addDetail(nCn5b, pv5a,  60, bd(75_000),  "LOT-CN5-VIT-001");
        addDetail(nCn5b, pv6a, 100, bd(85_000),  "LOT-CN5-VOIA-001");
        addDetail(nCn5b, pv8a,  30, bd(280_000), "LOT-CN5-OTC-001");

        // Chi Nhánh An Giang — 2 phiếu (nhiều cá da trơn)
        InventoryNote nCn6a = saveNote("PN-CN6-001", branch6, supTom, kho6, now.minusDays(52), InventoryNoteStatus.COMPLETED, bd(42_700_000));
        addDetail(nCn6a, pv9a,  80, bd(120_000), "LOT-CN6-CAT-001");
        addDetail(nCn6a, pv9b,  40, bd(550_000), "LOT-CN6-CAT-001");
        addDetail(nCn6a, pv3a,  60, bd(85_000),  "LOT-CN6-MIN-001");
        addDetail(nCn6a, pv5a,  80, bd(75_000),  "LOT-CN6-VIT-001");

        InventoryNote nCn6b = saveNote("PN-CN6-002", branch6, supCP,  kho6, now.minusDays(22), InventoryNoteStatus.PENDING,   bd(29_950_000));
        addDetail(nCn6b, pv1a,  60, bd(145_000), "LOT-CN6-CP-001");
        addDetail(nCn6b, pv2a,  50, bd(35_000),  "LOT-CN6-TBY-001");
        addDetail(nCn6b, pv6a, 100, bd(85_000),  "LOT-CN6-VOIA-001");
        addDetail(nCn6b, pv7a,  50, bd(220_000), "LOT-CN6-BIO-001");

        // Chi Nhánh Bến Tre — 2 phiếu
        InventoryNote nCn7a = saveNote("PN-CN7-001", branch7, supCP,  kho7, now.minusDays(46), InventoryNoteStatus.COMPLETED, bd(47_090_000));
        addDetail(nCn7a, pv1a,  90, bd(145_000), "LOT-CN7-CP-001");
        addDetail(nCn7a, pv1b,  45, bd(660_000), "LOT-CN7-CP-001");
        addDetail(nCn7a, pv4a,  70, bd(62_000),  "LOT-CN7-EM-001");

        InventoryNote nCn7b = saveNote("PN-CN7-002", branch7, supGN,  kho7, now.minusDays(15), InventoryNoteStatus.PENDING,   bd(76_800_000));
        addDetail(nCn7b, pv3b,  50, bd(380_000), "LOT-CN7-MIN-001");
        addDetail(nCn7b, pv5b,  60, bd(330_000), "LOT-CN7-VIT-001");
        addDetail(nCn7b, pv7b,  40, bd(950_000), "LOT-CN7-BIO-001");

        // Chi Nhánh Trà Vinh — 2 phiếu
        InventoryNote nCn8a = saveNote("PN-CN8-001", branch8, supCP,  kho8, now.minusDays(44), InventoryNoteStatus.COMPLETED, bd(25_020_000));
        addDetail(nCn8a, pv1a,  80, bd(145_000), "LOT-CN8-CP-001");
        addDetail(nCn8a, pv2b,  40, bd(130_000), "LOT-CN8-TBY-001");
        addDetail(nCn8a, pv4a,  60, bd(62_000),  "LOT-CN8-EM-001");
        addDetail(nCn8a, pv5a,  60, bd(75_000),  "LOT-CN8-VIT-001");

        InventoryNote nCn8b = saveNote("PN-CN8-002", branch8, supMW,  kho8, now.minusDays(12), InventoryNoteStatus.PENDING,   bd(27_900_000));
        addDetail(nCn8b, pv6a, 100, bd(85_000),  "LOT-CN8-VOIA-001");
        addDetail(nCn8b, pv7a,  50, bd(220_000), "LOT-CN8-BIO-001");
        addDetail(nCn8b, pv8a,  30, bd(280_000), "LOT-CN8-OTC-001");

        // Chi Nhánh Tiền Giang — 2 phiếu (tôm + cá)
        InventoryNote nCn9a = saveNote("PN-CN9-001", branch9, supTom, kho9, now.minusDays(42), InventoryNoteStatus.COMPLETED, bd(38_250_000));
        addDetail(nCn9a, pv9a,  70, bd(120_000), "LOT-CN9-CAT-001");
        addDetail(nCn9a, pv9b,  30, bd(550_000), "LOT-CN9-CAT-001");
        addDetail(nCn9a, pv1a,  80, bd(145_000), "LOT-CN9-CP-001");
        addDetail(nCn9a, pv2a,  50, bd(35_000),  "LOT-CN9-TBY-001");

        InventoryNote nCn9b = saveNote("PN-CN9-002", branch9, supGN,  kho9, now.minusDays(10), InventoryNoteStatus.PENDING,   bd(19_800_000));
        addDetail(nCn9b, pv3a,  80, bd(85_000),  "LOT-CN9-MIN-001");
        addDetail(nCn9b, pv6a, 100, bd(85_000),  "LOT-CN9-VOIA-001");
        addDetail(nCn9b, pv5a,  60, bd(75_000),  "LOT-CN9-VIT-001");

        // Chi Nhánh Long An — 2 phiếu
        InventoryNote nCn10a = saveNote("PN-CN10-001", branch10, supCP,  kho10, now.minusDays(40), InventoryNoteStatus.COMPLETED, bd(31_520_000));
        addDetail(nCn10a, pv1a, 100, bd(145_000), "LOT-CN10-CP-001");
        addDetail(nCn10a, pv2b,  50, bd(130_000), "LOT-CN10-TBY-001");
        addDetail(nCn10a, pv3a,  80, bd(85_000),  "LOT-CN10-MIN-001");
        addDetail(nCn10a, pv4a,  60, bd(62_000),  "LOT-CN10-EM-001");

        InventoryNote nCn10b = saveNote("PN-CN10-002", branch10, supGN,  kho10, now.minusDays(8),  InventoryNoteStatus.PENDING,   bd(35_400_000));
        addDetail(nCn10b, pv5b,  60, bd(330_000), "LOT-CN10-VIT-001");
        addDetail(nCn10b, pv6a,  80, bd(85_000),  "LOT-CN10-VOIA-001");
        addDetail(nCn10b, pv7a,  40, bd(220_000), "LOT-CN10-BIO-001");

        // Chi Nhánh Vĩnh Long — 2 phiếu
        InventoryNote nCn11a = saveNote("PN-CN11-001", branch11, supCP,  kho11, now.minusDays(38), InventoryNoteStatus.COMPLETED, bd(28_600_000));
        addDetail(nCn11a, pv1a,  90, bd(145_000), "LOT-CN11-CP-001");
        addDetail(nCn11a, pv9a,  60, bd(120_000), "LOT-CN11-CAT-001");
        addDetail(nCn11a, pv4a,  50, bd(62_000),  "LOT-CN11-EM-001");
        addDetail(nCn11a, pv5a,  70, bd(75_000),  "LOT-CN11-VIT-001");

        InventoryNote nCn11b = saveNote("PN-CN11-002", branch11, supMW,  kho11, now.minusDays(5),  InventoryNoteStatus.PENDING,   bd(20_500_000));
        addDetail(nCn11b, pv2b,  40, bd(130_000), "LOT-CN11-TBY-001");
        addDetail(nCn11b, pv3a,  80, bd(85_000),  "LOT-CN11-MIN-001");
        addDetail(nCn11b, pv6a, 100, bd(85_000),  "LOT-CN11-VOIA-001");

        // Chi Nhánh Đồng Tháp — 2 phiếu (nhiều cá)
        InventoryNote nCn12a = saveNote("PN-CN12-001", branch12, supTom, kho12, now.minusDays(35), InventoryNoteStatus.COMPLETED, bd(59_300_000));
        addDetail(nCn12a, pv9a, 120, bd(120_000), "LOT-CN12-CAT-001");
        addDetail(nCn12a, pv9b,  60, bd(550_000), "LOT-CN12-CAT-001");
        addDetail(nCn12a, pv3a,  80, bd(85_000),  "LOT-CN12-MIN-001");
        addDetail(nCn12a, pv6a,  60, bd(85_000),  "LOT-CN12-VOIA-001");

        InventoryNote nCn12b = saveNote("PN-CN12-002", branch12, supCP,  kho12, now.minusDays(4),  InventoryNoteStatus.PENDING,   bd(20_310_000));
        addDetail(nCn12b, pv1a,  70, bd(145_000), "LOT-CN12-CP-001");
        addDetail(nCn12b, pv2b,  40, bd(130_000), "LOT-CN12-TBY-001");
        addDetail(nCn12b, pv4a,  80, bd(62_000),  "LOT-CN12-EM-001");

        // ── 12. KHÁCH HÀNG ──────────────────────────────────────────────
        customerRepository.save(Customer.builder().user(user1).name("Nguyễn Văn Tôm").phone("0911000001").email("user1@gmail.com").gender(CustomerGender.MALE).provinceId("79").addressDetail("Cà Mau").status(CustomerStatus.ACTIVE).build());
        customerRepository.save(Customer.builder().user(user2).name("Trần Thị Cua").phone("0911000002").email("user2@gmail.com").gender(CustomerGender.FEMALE).provinceId("94").addressDetail("Sóc Trăng").status(CustomerStatus.ACTIVE).build());

        // ── 12. ĐƠN HÀNG ────────────────────────────────────────────────
        Order o1 = orderRepository.save(Order.builder()
                .code("DH-2024-001").branch(branch1).user(user1).status(OrderStatus.COMPLETED)
                .paymentMethod(PaymentMethod.COD).paymentStatus(PaymentStatus.PAID)                .shippingAddress("Cà Mau").totalAmount(bd(420_000)).discountAmount(BigDecimal.ZERO).finalAmount(bd(420_000))
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

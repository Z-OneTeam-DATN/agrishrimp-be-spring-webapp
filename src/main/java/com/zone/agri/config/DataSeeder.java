package com.zone.agri.config;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Permission;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.PermissionGroup;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.PermissionRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Kiểm tra nếu DB đã có Role rồi thì không chạy nữa (tránh trùng lặp)
        if (roleRepository.count() > 0) {
            log.info(">>> Dữ liệu đã tồn tại, bỏ qua bước khởi tạo Data Seeder.");
            return;
        }

        log.info(">>> ĐANG KHỞI TẠO DỮ LIỆU MẪU...");

        // ==========================================
        // 1. TẠO PERMISSIONS (QUYỀN HẠN)
        // ==========================================

        // ===== GROUP: USER & ACCOUNT =====
        Permission pUserView   = createPermission("Xem hồ sơ", "USER_VIEW", PermissionGroup.USER_PROFILE);
        Permission pUserCreate = createPermission("Tạo tài khoản", "USER_CREATE", PermissionGroup.SYSTEM_ACCOUNT);
        Permission pUserUpdate = createPermission("Sửa tài khoản", "USER_UPDATE", PermissionGroup.SYSTEM_ACCOUNT);
        Permission pUserDelete = createPermission("Xóa tài khoản", "USER_DELETE", PermissionGroup.SYSTEM_ACCOUNT);

        // ===== GROUP: PRODUCT =====
        Permission pProductView   = createPermission("Xem sản phẩm", "PRODUCT_VIEW", PermissionGroup.PRODUCT_CATALOG);
        Permission pProductCreate = createPermission("Tạo sản phẩm", "PRODUCT_CREATE", PermissionGroup.PRODUCT_CATALOG);
        Permission pProductUpdate = createPermission("Sửa sản phẩm", "PRODUCT_UPDATE", PermissionGroup.PRODUCT_CATALOG);
        Permission pProductDelete = createPermission("Xóa sản phẩm", "PRODUCT_DELETE", PermissionGroup.PRODUCT_CATALOG);

        // ===== GROUP: ORDER =====
        Permission pOrderView   = createPermission("Xem đơn hàng", "ORDER_VIEW", PermissionGroup.ORDER_TRANSACTION);
        Permission pOrderManage = createPermission("Xử lý đơn hàng", "ORDER_MANAGE", PermissionGroup.ORDER_TRANSACTION);

        // ===== GROUP: FARM & AI =====
        Permission pFarmManage = createPermission("Quản lý trang trại & AI", "FARM_MANAGE", PermissionGroup.FARM_MANAGEMENT_AI);

        // ===== GROUP: WAREHOUSE =====
        Permission pWarehouseManage = createPermission("Quản lý kho vận", "WAREHOUSE_MANAGE", PermissionGroup.WAREHOUSE_LOGISTICS);

        // ===== GROUP: PROMOTION =====
        Permission pVoucherManage = createPermission("Quản lý Voucher", "VOUCHER_MANAGE", PermissionGroup.PROMOTION_VOUCHER);

        // ===== GROUP: REPORT =====
        Permission pReportView = createPermission("Xem báo cáo", "REPORT_VIEW", PermissionGroup.REPORT_DASHBOARD);

        // ===== GROUP: NOTIFICATION =====
        Permission pNotifyManage = createPermission("Quản lý thông báo", "NOTIFICATION_MANAGE", PermissionGroup.NOTIFICATION_MANAGEMENT);


        // ==========================================
        // 2. TẠO ROLES (VAI TRÒ)
        // ==========================================

        // --- Role ADMIN: Full quyền ---
        // Gom tất cả permission vừa tạo vào List này
        Set<Permission> adminPerms = new HashSet<>(List.of(
                pUserView, pUserCreate, pUserUpdate, pUserDelete,
                pProductView, pProductCreate, pProductUpdate, pProductDelete,
                pOrderView, pOrderManage,
                pFarmManage,
                pWarehouseManage,
                pVoucherManage,
                pReportView,
                pNotifyManage
        ));
        Role adminRole = createRole("ADMIN", "Quản Trị Viên", true, adminPerms);

        // --- Role STAFF: Nhân viên (Không được xóa User, Không xem Báo cáo tài chính) ---
        Set<Permission> staffPerms = new HashSet<>(List.of(
                pUserView, // Chỉ xem user
                pProductView, pProductCreate, pProductUpdate, // Quản lý sản phẩm (ko xóa)
                pOrderView, pOrderManage, // Xử lý đơn
                pWarehouseManage, // Quản lý kho
                pVoucherManage,   // Tạo khuyến mãi
                pNotifyManage     // Gửi thông báo
        ));
        Role staffRole = createRole("STAFF", "Nhân Viên", false, staffPerms);

        // --- Role FARMER: Nông Dân (Quan trọng nhất là FARM_MANAGE) ---
        Set<Permission> farmerPerms = new HashSet<>(List.of(
                pUserView,      // Xem hồ sơ cá nhân
                pProductView,   // Xem vật tư để mua
                pOrderView,     // Xem đơn hàng của mình
                pFarmManage     // QUYỀN QUAN TRỌNG: Quản lý ao nuôi, xem AI
        ));
        Role farmerRole = createRole("FARMER", "Nông Dân", false, farmerPerms);

        // --- Role USER: Khách vãng lai / Người mua hàng thường ---
        Set<Permission> userPerms = new HashSet<>(List.of(
                pUserView,      // Xem hồ sơ
                pProductView,   // Xem sản phẩm
                pOrderView      // Xem đơn hàng cá nhân
        ));
        Role userRole = createRole("USER", "Người Dùng", false, userPerms);

        // ==========================================
        // 3. TẠO BRANCH (CHI NHÁNH MẪU)
        // ==========================================
        Branch mainBranch = Branch.builder()
                .branchCode("CN_CT_01")
                .name("Trụ sở chính Cần Thơ")
                .phone("0292388888")
                .email("contact@agrishrimp.vn")
                .addressDetail("Ninh Kiều, Cần Thơ")
                .provinceId(92)
                .districtId(916)
                .status(BranchStatus.ACTIVE)
                .build();

        mainBranch = branchRepository.save(mainBranch);

        // ==========================================
        // 4. TẠO TÀI KHOẢN ADMIN MẪU
        // ==========================================
        if (!userRepository.existsByEmail("admin@gmail.com")) {
            User admin = User.builder()
                    .fullName("Super Admin")
                    .email("admin@gmail.com")
                    .phoneNumber("0999999999")
                    .passwordHash(passwordEncoder.encode("123456")) // Pass: 123456
                    .dateOfBirth(LocalDate.of(1990, 1, 1))
                    .status(UserStatus.ACTIVE)
                    .role(adminRole)         // Role Admin
                    .branch(mainBranch)      // Thuộc chi nhánh chính
                    .provider(AuthProvider.LOCAL) // Đăng nhập thường
                    .avatarUrl("https://ui-avatars.com/api/?name=Super+Admin&background=random")
                    .build();

            userRepository.save(admin);
            log.info(">>> Đã tạo tài khoản Admin mẫu: admin@gmail.com / 123456");
        }

        log.info(">>> KHỞI TẠO DỮ LIỆU HOÀN TẤT!");
    }

    // --- HELPER METHODS ---

    private Permission createPermission(String name, String code, PermissionGroup group) {
        return permissionRepository.save(Permission.builder()
                .name(name)
                .code(code)
                .groupName(group)
                .build());
    }

    private Role createRole(String slug, String displayName, boolean isSystem, Set<Permission> permissions) {
        return roleRepository.save(Role.builder()
                .slug(slug)
                .displayName(displayName)
                .isSystem(isSystem)
                .description("Vai trò " + displayName + " hệ thống")
                .permissions(permissions)
                .build());
    }
}
package com.zone.agri.config;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Permission;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.PermissionGroup;
import com.zone.agri.entity.enums.PermissionType;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        log.info(">>> ĐANG KIỂM TRA VÀ KHỞI TẠO DỮ LIỆU MẪU...");

        // ==========================================
        // 1. TẠO PERMISSIONS (QUYỀN HẠN CHA - CON)
        // ==========================================

        // ===== 1. GROUP: HỆ THỐNG (SYSTEM) =====
        Permission pDashboard = createPermission("Tổng quan", "DASHBOARD_VIEW", PermissionGroup.SYSTEM, PermissionType.MODULE, null);
        Permission pInventoryDashboard = createPermission("Bàn làm việc kho", "INVENTORY_DASHBOARD_VIEW", PermissionGroup.SYSTEM, PermissionType.MODULE, null);

        // ===== 2. GROUP: QUẢN TRỊ (ADMINISTRATION) =====
        Permission pUserModule = createPermission("Nhân viên hệ thống", "USER_MANAGE", PermissionGroup.ADMINISTRATION, PermissionType.MODULE, null);
        Permission pUserCreate = createPermission("Tạo nhân viên", "USER_CREATE", PermissionGroup.ADMINISTRATION, PermissionType.ACTION, pUserModule.getId());
        Permission pUserUpdate = createPermission("Sửa nhân viên", "USER_UPDATE", PermissionGroup.ADMINISTRATION, PermissionType.ACTION, pUserModule.getId());
        Permission pUserDelete = createPermission("Xóa nhân viên", "USER_DELETE", PermissionGroup.ADMINISTRATION, PermissionType.ACTION, pUserModule.getId());

        Permission pBranchModule = createPermission("Chi nhánh & Kho", "BRANCH_MANAGE", PermissionGroup.ADMINISTRATION, PermissionType.MODULE, null);

        // ===== 3. GROUP: HÀNG HÓA (PRODUCT_CATALOG) =====
        Permission pProductModule = createPermission("Sản phẩm", "PRODUCT_MANAGE", PermissionGroup.PRODUCT_CATALOG, PermissionType.MODULE, null);
        Permission pProductCreate = createPermission("Tạo sản phẩm", "PRODUCT_CREATE", PermissionGroup.PRODUCT_CATALOG, PermissionType.ACTION, pProductModule.getId());
        Permission pProductUpdate = createPermission("Sửa sản phẩm", "PRODUCT_UPDATE", PermissionGroup.PRODUCT_CATALOG, PermissionType.ACTION, pProductModule.getId());
        Permission pProductDelete = createPermission("Xóa sản phẩm", "PRODUCT_DELETE", PermissionGroup.PRODUCT_CATALOG, PermissionType.ACTION, pProductModule.getId());

        Permission pCategoryModule = createPermission("Danh mục", "CATEGORY_MANAGE", PermissionGroup.PRODUCT_CATALOG, PermissionType.MODULE, null);
        Permission pVariantModule = createPermission("Thuộc tính", "VARIANT_MANAGE", PermissionGroup.PRODUCT_CATALOG, PermissionType.MODULE, null);

        // ===== 4. GROUP: GIAO DỊCH KHO (INVENTORY_TRANSACTION) =====
        Permission pImportModule = createPermission("Nhập hàng", "IMPORT_MANAGE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.MODULE, null);
        Permission pImportApprove = createPermission("Duyệt phiếu nhập", "IMPORT_APPROVE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.ACTION, pImportModule.getId());

        Permission pExportModule = createPermission("Xuất hàng", "EXPORT_MANAGE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.MODULE, null);
        Permission pExportApprove = createPermission("Duyệt lệnh xuất kho", "EXPORT_APPROVE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.ACTION, pExportModule.getId());
        Permission pExportForceEdit = createPermission("Sửa/Xóa phiếu đã hoàn thành", "EXPORT_FORCE_EDIT", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.ACTION, pExportModule.getId());

        Permission pTransferModule = createPermission("Điều chuyển", "TRANSFER_MANAGE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.MODULE, null);
        Permission pTransferApprove = createPermission("Duyệt lệnh điều chuyển", "TRANSFER_APPROVE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.ACTION, pTransferModule.getId());

        Permission pInventoryCheckModule = createPermission("Kiểm kê hàng hóa", "INVENTORY_CHECK_MANAGE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.MODULE, null);
        Permission pInventoryBalance = createPermission("Chốt sổ / Cân bằng kho", "INVENTORY_BALANCE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.ACTION, pInventoryCheckModule.getId());

        // ===== 5. GROUP: VẬN CHUYỂN (SHIPPING) =====
        Permission pShippingModule = createPermission("Tổng quan vận chuyển", "SHIPPING_MANAGE", PermissionGroup.SHIPPING, PermissionType.MODULE, null);

        // ===== 6. GROUP: ĐỐI TÁC (PARTNER) =====
        Permission pSupplierModule = createPermission("Nhà cung cấp", "SUPPLIER_MANAGE", PermissionGroup.PARTNER, PermissionType.MODULE, null);
        Permission pCustomerModule = createPermission("Khách hàng", "CUSTOMER_MANAGE", PermissionGroup.PARTNER, PermissionType.MODULE, null);

        // ===== 7. GROUP: BÁO CÁO (REPORT) =====
        Permission pReportSales = createPermission("Báo cáo bán hàng", "REPORT_SALES_VIEW", PermissionGroup.REPORT, PermissionType.MODULE, null);
        Permission pReportInventory = createPermission("Báo cáo kho", "REPORT_INVENTORY_VIEW", PermissionGroup.REPORT, PermissionType.MODULE, null);
        Permission pReportFinancial = createPermission("Báo cáo tài chính", "REPORT_FINANCIAL_VIEW", PermissionGroup.REPORT, PermissionType.MODULE, null);

        // ===== 8. GROUP: CÀI ĐẶT (SETTING) =====
        Permission pSettingModule = createPermission("Cài đặt hệ thống", "SETTING_MANAGE", PermissionGroup.SETTING, PermissionType.MODULE, null);
        Permission pRoleModule = createPermission("Phân quyền vai trò", "ROLE_MANAGE", PermissionGroup.SETTING, PermissionType.MODULE, null);

        // ==========================================
        // 2. TẠO ROLES (VAI TRÒ)
        // ==========================================

        // --- Role ADMIN: Full toàn bộ quyền ---
        Set<Permission> adminPerms = Stream.of(
                pDashboard, pInventoryDashboard,
                pUserModule, pUserCreate, pUserUpdate, pUserDelete, pBranchModule,
                pProductModule, pProductCreate, pProductUpdate, pProductDelete, pCategoryModule, pVariantModule,
                pImportModule, pImportApprove, pExportModule, pExportApprove, pExportForceEdit,
                pTransferModule, pTransferApprove, pInventoryCheckModule, pInventoryBalance,
                pShippingModule,
                pSupplierModule, pCustomerModule,
                pReportSales, pReportInventory, pReportFinancial,
                pSettingModule, pRoleModule
        ).collect(Collectors.toSet());
        Role adminRole = createRole("ADMIN", "Quản Trị Viên", true, adminPerms);

        // --- Role KHO ---
        Set<Permission> staffKhoPerms = Stream.of(
                pInventoryDashboard, pProductModule, pCategoryModule, pVariantModule,
                pImportModule, pExportModule, pTransferModule, pInventoryCheckModule,
                pShippingModule, pSupplierModule, pReportInventory
        ).collect(Collectors.toSet());
        createRole("STAFF_KHO", "Thủ Kho", false, staffKhoPerms);

        // --- Role BÁN HÀNG ---
        Set<Permission> salesPerms = Stream.of(
                pDashboard, pProductModule, pCustomerModule, pReportSales
        ).collect(Collectors.toSet());
        createRole("SALES", "Nhân viên Bán hàng", false, salesPerms);

        // --- Role USER: Người dùng mặc định (đăng ký từ web/app) ---
        createRole("USER", "Người dùng", false, Set.of());

        // ==========================================
        // 3. TẠO BRANCH (CHI NHÁNH MẪU)
        // ==========================================
        Branch mainBranch = branchRepository.findByBranchCode("CN_CT_01").orElse(null);
        
        // Nếu không tìm thấy theo mã, kiểm tra tiếp theo số điện thoại để tránh lỗi Duplicate
        if (mainBranch == null) {
            mainBranch = branchRepository.findByPhone("0292388888").orElse(null);
        }

        if (mainBranch == null) {
            mainBranch = Branch.builder()
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
            log.info(">>> Đã tạo Chi nhánh mẫu thành công!");
        } else {
            log.info(">>> Chi nhánh mẫu đã tồn tại (Mã: {} hoặc SĐT: {}), sử dụng bản ghi hiện có.", 
                     mainBranch.getBranchCode(), mainBranch.getPhone());
        }

        // ==========================================
        // 4. TẠO TÀI KHOẢN ADMIN MẪU
        // ==========================================
        if (!userRepository.existsByEmail("admin@gmail.com")) {
            User admin = User.builder()
                    .fullName("Super Admin")
                    .email("admin@gmail.com")
                    .phoneNumber("0999999999")
                    .passwordHash(passwordEncoder.encode("123456"))
                    .dateOfBirth(LocalDate.of(1990, 1, 1))
                    .status(UserStatus.ACTIVE)
                    .role(adminRole)
                    .branch(mainBranch)
                    .provider(AuthProvider.LOCAL)
                    .avatarUrl("https://ui-avatars.com/api/?name=Super+Admin&background=random")
                    .build();

            userRepository.save(admin);
            log.info(">>> Đã tạo tài khoản Admin mẫu: admin@gmail.com / 123456");
        }

        log.info(">>> KHỞI TẠO DỮ LIỆU HOÀN TẤT!");
    }

    // --- HELPER METHODS ---

    private Permission createPermission(String name, String code, PermissionGroup group, PermissionType type, Long parentId) {
        return permissionRepository.findByCode(code).orElseGet(() -> 
            permissionRepository.save(Permission.builder()
                .name(name)
                .code(code)
                .groupName(group)
                .type(type)
                .parentId(parentId)
                .build())
        );
    }

    private Role createRole(String slug, String displayName, boolean isSystem, Set<Permission> permissions) {
        return roleRepository.findBySlug(slug).orElseGet(() -> 
            roleRepository.save(Role.builder()
                .slug(slug)
                .displayName(displayName)
                .isSystem(isSystem)
                .description("Vai trò " + displayName + " hệ thống")
                .permissions(permissions)
                .build())
        );
    }
}

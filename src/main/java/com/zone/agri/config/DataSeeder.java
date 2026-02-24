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
        log.info(">>> INITIALIZING SYSTEM DATA...");

        // 1. Create Branches
        Branch mainBranch = branchRepository.findByBranchCode("MAIN_WH").orElseGet(() ->
            branchRepository.save(Branch.builder()
                .branchCode("MAIN_WH")
                .branchType("WAREHOUSE")
                .name("Main Warehouse")
                .phone("0111111111")
                .email("main@agrishrimp.com")
                .addressDetail("Hanoi")
                .status(BranchStatus.ACTIVE)
                .build())
        );

        Branch branch1 = branchRepository.findByBranchCode("BRANCH_01").orElseGet(() ->
            branchRepository.save(Branch.builder()
                .branchCode("BRANCH_01")
                .branchType("STORE")
                .name("Branch 01")
                .phone("0222222222")
                .email("branch1@agrishrimp.com")
                .addressDetail("Can Tho")
                .status(BranchStatus.ACTIVE)
                .build())
        );

        // 2. Create Permissions
        Permission pStockRequestCreate = createPermission("Tạo yêu cầu bổ sung kho", "STOCK_REQUEST_CREATE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.ACTION);
        Permission pStockRequestApprove = createPermission("Duyệt yêu cầu bổ sung kho", "STOCK_REQUEST_APPROVE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.ACTION);
        Permission pInventoryView = createPermission("Xem tồn kho", "INVENTORY_VIEW", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.ACTION);
        
        // Modules
        Permission pImportManage = createPermission("Quản lý nhập", "IMPORT_MANAGE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.MODULE);
        Permission pExportManage = createPermission("Quản lý xuất", "EXPORT_MANAGE", PermissionGroup.INVENTORY_TRANSACTION, PermissionType.MODULE);

        // 3. Create Roles
        Role superAdminRole = createRole("SUPER_ADMIN", "Super Admin", true, 
            Set.of(pStockRequestCreate, pStockRequestApprove, pInventoryView, pImportManage, pExportManage));
        
        Role branchManagerRole = createRole("BRANCH_MANAGER", "Branch Manager", false, 
            Set.of(pStockRequestCreate, pInventoryView, pImportManage, pExportManage));
            
        Role userRole = createRole("USER", "Regular User", false, Set.of());

        // 4. Create Accounts
        createAccount("superadmin@gmail.com", "Super Admin", "123456", superAdminRole, mainBranch);
        createAccount("branch1@gmail.com", "Branch Manager 1", "123456", branchManagerRole, branch1);
        createAccount("user1@gmail.com", "Regular User 1", "123456", userRole, null);

        log.info(">>> DATA INITIALIZATION COMPLETE.");
    }

    private void createAccount(String email, String name, String pass, Role role, Branch branch) {
        if (!userRepository.existsByEmail(email)) {
            userRepository.save(User.builder()
                .fullName(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(pass))
                .status(UserStatus.ACTIVE)
                .role(role)
                .branch(branch)
                .provider(AuthProvider.LOCAL)
                .build());
            log.info(">>> Created account: {} / {}", email, pass);
        }
    }

    private Permission createPermission(String name, String code, PermissionGroup group, PermissionType type) {
        return permissionRepository.findByCode(code).orElseGet(() -> 
            permissionRepository.save(Permission.builder()
                .name(name)
                .code(code)
                .groupName(group)
                .type(type)
                .build())
        );
    }

    private Role createRole(String slug, String displayName, boolean isSystem, Set<Permission> perms) {
        return roleRepository.findBySlug(slug).orElseGet(() -> 
            roleRepository.save(Role.builder()
                .slug(slug)
                .displayName(displayName)
                .isSystem(isSystem)
                .isActive(true)
                .description("Vai trò " + displayName + " hệ thống")
                .permissions(perms)
                .build())
        );
    }
}

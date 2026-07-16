package com.zone.agri.config;

import com.zone.agri.entity.Permission;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.Gender;
import com.zone.agri.entity.enums.PermissionGroup;
import com.zone.agri.entity.enums.PermissionType;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.repository.PermissionRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.startup.seed-data.enabled", havingValue = "true", matchIfMissing = true)
@org.springframework.core.annotation.Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        boolean hasExistingRoles = roleRepository.count() > 0;

        log.info(">>> ĐỒNG BỘ DỮ LIỆU NỀN TẢNG HỆ THỐNG (permissions, roles, users)...");

        Permission mDash = pMod("Tổng quan hệ thống", "DASHBOARD", PermissionGroup.SYSTEM);
        Permission aDashV = pAct("Xem tổng quan", "DASHBOARD_VIEW", PermissionGroup.SYSTEM, mDash);
        Permission mWspace = pMod("Bàn làm việc", "WORKSPACE", PermissionGroup.SYSTEM);
        Permission aWspaceV = pAct("Xem bàn làm việc", "WORKSPACE_VIEW", PermissionGroup.SYSTEM, mWspace);

        Permission mRpt = pMod("Báo cáo", "REPORT", PermissionGroup.REPORT);
        Permission aRptSale = pAct("Báo cáo doanh thu", "REPORT_REVENUE_VIEW", PermissionGroup.REPORT, mRpt);
        Permission aRptInv = pAct("Báo cáo kho", "REPORT_INVENTORY_VIEW", PermissionGroup.REPORT, mRpt);
        Permission aRptFin = pAct("Báo cáo tài chính", "REPORT_FINANCE_VIEW", PermissionGroup.REPORT, mRpt);

        Permission mUser = pMod("Quản lý nhân viên", "STAFF", PermissionGroup.ADMINISTRATION);
        Permission aUserV = pAct("Xem nhân viên", "STAFF_VIEW", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserC = pAct("Thêm nhân viên", "STAFF_CREATE", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserU = pAct("Sửa nhân viên", "STAFF_UPDATE", PermissionGroup.ADMINISTRATION, mUser);
        Permission aUserD = pAct("Xóa nhân viên", "STAFF_DELETE", PermissionGroup.ADMINISTRATION, mUser);

        Permission mBranch = pMod("Quản lý chi nhánh", "BRANCH", PermissionGroup.ADMINISTRATION);
        Permission aBranchV = pAct("Xem chi nhánh", "BRANCH_VIEW", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchC = pAct("Thêm chi nhánh", "BRANCH_CREATE", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchU = pAct("Sửa chi nhánh", "BRANCH_UPDATE", PermissionGroup.ADMINISTRATION, mBranch);
        Permission aBranchD = pAct("Xóa chi nhánh", "BRANCH_DELETE", PermissionGroup.ADMINISTRATION, mBranch);

        Permission mRole = pMod("Quản lý vai trò", "ROLE", PermissionGroup.ADMINISTRATION);
        Permission aRoleV = pAct("Xem vai trò", "ROLE_VIEW", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleC = pAct("Tạo vai trò", "ROLE_CREATE", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleU = pAct("Sửa vai trò", "ROLE_UPDATE", PermissionGroup.ADMINISTRATION, mRole);
        Permission aRoleD = pAct("Xóa vai trò", "ROLE_DELETE", PermissionGroup.ADMINISTRATION, mRole);

        Permission mOrder = pMod("Quản lý đơn hàng", "ORDER", PermissionGroup.SALES);
        Permission aOrdV = pAct("Xem đơn hàng", "ORDER_VIEW", PermissionGroup.SALES, mOrder);
        Permission aOrdC = pAct("Tạo đơn hàng", "ORDER_CREATE", PermissionGroup.SALES, mOrder);
        Permission aOrdU = pAct("Cập nhật đơn hàng", "ORDER_UPDATE", PermissionGroup.SALES, mOrder);
        Permission aOrdCnf = pAct("Xác nhận đơn hàng", "ORDER_CONFIRM", PermissionGroup.SALES, mOrder);
        Permission aOrdShip = pAct("Giao hàng", "ORDER_SHIP", PermissionGroup.SALES, mOrder);
        Permission aOrdX = pAct("Huỷ đơn hàng", "ORDER_CANCEL", PermissionGroup.SALES, mOrder);
        Permission aOrdDone = pAct("Hoàn tất đơn hàng", "ORDER_COMPLETE", PermissionGroup.SALES, mOrder);
        Permission aOrdExport = pAct("Xuất danh sách đơn hàng", "ORDER_EXPORT", PermissionGroup.SALES, mOrder);
        Permission aOrdRefund = pAct("Hoàn tiền đơn hàng", "ORDER_REFUND", PermissionGroup.SALES, mOrder);
        Permission aOrdD = pAct("Xóa đơn hàng", "ORDER_DELETE", PermissionGroup.SALES, mOrder);

        Permission mCus = pMod("Quản lý khách hàng", "CUSTOMER", PermissionGroup.SALES);
        Permission aCusV = pAct("Xem khách hàng", "CUSTOMER_VIEW", PermissionGroup.SALES, mCus);
        Permission aCusC = pAct("Thêm khách hàng", "CUSTOMER_CREATE", PermissionGroup.SALES, mCus);
        Permission aCusU = pAct("Sửa khách hàng", "CUSTOMER_UPDATE", PermissionGroup.SALES, mCus);
        Permission aCusD = pAct("Xóa khách hàng", "CUSTOMER_DELETE", PermissionGroup.SALES, mCus);

        Permission mVou = pMod("Quản lý mã giảm giá", "VOUCHER", PermissionGroup.SALES);
        Permission aVouV = pAct("Xem mã giảm giá", "VOUCHER_VIEW", PermissionGroup.SALES, mVou);
        Permission aVouC = pAct("Thêm mã giảm giá", "VOUCHER_CREATE", PermissionGroup.SALES, mVou);
        Permission aVouU = pAct("Sửa mã giảm giá", "VOUCHER_UPDATE", PermissionGroup.SALES, mVou);
        Permission aVouD = pAct("Xóa mã giảm giá", "VOUCHER_DELETE", PermissionGroup.SALES, mVou);

        Permission mProd = pMod("Quản lý sản phẩm", "PRODUCT", PermissionGroup.PRODUCT_CATALOG);
        Permission aProdV = pAct("Xem sản phẩm", "PRODUCT_VIEW", PermissionGroup.PRODUCT_CATALOG, mProd);
        Permission aProdC = pAct("Thêm sản phẩm", "PRODUCT_CREATE", PermissionGroup.PRODUCT_CATALOG, mProd);
        Permission aProdU = pAct("Sửa sản phẩm", "PRODUCT_UPDATE", PermissionGroup.PRODUCT_CATALOG, mProd);
        Permission aProdD = pAct("Xóa sản phẩm", "PRODUCT_DELETE", PermissionGroup.PRODUCT_CATALOG, mProd);

        Permission mCat = pMod("Quản lý danh mục", "CATEGORY", PermissionGroup.PRODUCT_CATALOG);
        Permission aCatV = pAct("Xem danh mục", "CATEGORY_VIEW", PermissionGroup.PRODUCT_CATALOG, mCat);
        Permission aCatC = pAct("Thêm danh mục", "CATEGORY_CREATE", PermissionGroup.PRODUCT_CATALOG, mCat);
        Permission aCatU = pAct("Sửa danh mục", "CATEGORY_UPDATE", PermissionGroup.PRODUCT_CATALOG, mCat);
        Permission aCatD = pAct("Xóa danh mục", "CATEGORY_DELETE", PermissionGroup.PRODUCT_CATALOG, mCat);

        Permission mAttr = pMod("Quản lý thuộc tính", "ATTRIBUTE", PermissionGroup.PRODUCT_CATALOG);
        Permission aAttrV = pAct("Xem thuộc tính", "ATTRIBUTE_VIEW", PermissionGroup.PRODUCT_CATALOG, mAttr);
        Permission aAttrC = pAct("Thêm thuộc tính", "ATTRIBUTE_CREATE", PermissionGroup.PRODUCT_CATALOG, mAttr);
        Permission aAttrU = pAct("Sửa thuộc tính", "ATTRIBUTE_UPDATE", PermissionGroup.PRODUCT_CATALOG, mAttr);
        Permission aAttrD = pAct("Xóa thuộc tính", "ATTRIBUTE_DELETE", PermissionGroup.PRODUCT_CATALOG, mAttr);

        Permission mSup = pMod("Quản lý nhà cung cấp", "SUPPLIER", PermissionGroup.INVENTORY);
        Permission aSupV = pAct("Xem nhà cung cấp", "SUPPLIER_VIEW", PermissionGroup.INVENTORY, mSup);
        Permission aSupC = pAct("Thêm nhà cung cấp", "SUPPLIER_CREATE", PermissionGroup.INVENTORY, mSup);
        Permission aSupU = pAct("Sửa nhà cung cấp", "SUPPLIER_UPDATE", PermissionGroup.INVENTORY, mSup);
        Permission aSupD = pAct("Xóa nhà cung cấp", "SUPPLIER_DELETE", PermissionGroup.INVENTORY, mSup);

        Permission mImp = pMod("Quản lý nhập hàng", "IMPORT", PermissionGroup.INVENTORY);
        Permission aImpV = pAct("Xem phiếu nhập", "IMPORT_VIEW", PermissionGroup.INVENTORY, mImp);
        Permission aImpC = pAct("Tạo phiếu nhập", "IMPORT_CREATE", PermissionGroup.INVENTORY, mImp);
        Permission aImpU = pAct("Sửa phiếu nhập", "IMPORT_UPDATE", PermissionGroup.INVENTORY, mImp);
        Permission aImpA = pAct("Duyệt phiếu nhập", "IMPORT_APPROVE", PermissionGroup.INVENTORY, mImp);
        Permission aImpX = pAct("Hủy phiếu nhập", "IMPORT_CANCEL", PermissionGroup.INVENTORY, mImp);
        Permission aImpD = pAct("Xóa phiếu nhập", "IMPORT_DELETE", PermissionGroup.INVENTORY, mImp);

        Permission mExp = pMod("Quản lý xuất hàng", "EXPORT", PermissionGroup.INVENTORY);
        Permission aExpV = pAct("Xem phiếu xuất", "EXPORT_VIEW", PermissionGroup.INVENTORY, mExp);
        Permission aExpC = pAct("Tạo phiếu xuất", "EXPORT_CREATE", PermissionGroup.INVENTORY, mExp);
        Permission aExpA = pAct("Duyệt phiếu xuất", "EXPORT_APPROVE", PermissionGroup.INVENTORY, mExp);
        Permission aExpU = pAct("Sửa phiếu xuất", "EXPORT_UPDATE", PermissionGroup.INVENTORY, mExp);
        Permission aExpX = pAct("Hủy phiếu xuất", "EXPORT_CANCEL", PermissionGroup.INVENTORY, mExp);
        Permission aExpD = pAct("Xóa phiếu xuất", "EXPORT_DELETE", PermissionGroup.INVENTORY, mExp);

        Permission mTrf = pMod("Quản lý điều chuyển", "TRANSFER", PermissionGroup.INVENTORY);
        Permission aTrfV = pAct("Xem phiếu điều chuyển", "TRANSFER_VIEW", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfC = pAct("Tạo phiếu điều chuyển", "TRANSFER_CREATE", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfA = pAct("Duyệt điều chuyển", "TRANSFER_APPROVE", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfX = pAct("Hủy điều chuyển", "TRANSFER_CANCEL", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfD = pAct("Xóa điều chuyển", "TRANSFER_DELETE", PermissionGroup.INVENTORY, mTrf);
        Permission aTrfU = pAct("Sửa điều chuyển", "TRANSFER_UPDATE", PermissionGroup.INVENTORY, mTrf);

        Permission mChk = pMod("Kiểm kê kho", "INVENTORY_CHECK", PermissionGroup.INVENTORY);
        Permission aChkV = pAct("Xem phiếu kiểm kê", "INVENTORY_CHECK_VIEW", PermissionGroup.INVENTORY, mChk);
        Permission aChkC = pAct("Tạo phiếu kiểm kê", "INVENTORY_CHECK_CREATE", PermissionGroup.INVENTORY, mChk);
        Permission aChkA = pAct("Duyệt phiếu kiểm kê", "INVENTORY_CHECK_APPROVE", PermissionGroup.INVENTORY, mChk);
        Permission aChkU = pAct("Sửa phiếu kiểm kê", "INVENTORY_CHECK_UPDATE", PermissionGroup.INVENTORY, mChk);
        Permission aChkX = pAct("Hủy phiếu kiểm kê", "INVENTORY_CHECK_CANCEL", PermissionGroup.INVENTORY, mChk);
        Permission aChkD = pAct("Xóa phiếu kiểm kê", "INVENTORY_CHECK_DELETE", PermissionGroup.INVENTORY, mChk);

        Permission mPurchaseRequest = pMod("Yêu cầu mua nhà cung cấp", "PURCHASE_REQUEST", PermissionGroup.INVENTORY);
        Permission aPurchaseRequestV = pAct("Xem yêu cầu mua", "PURCHASE_REQUEST_VIEW", PermissionGroup.INVENTORY, mPurchaseRequest);
        Permission aPurchaseRequestC = pAct("Tạo yêu cầu mua", "PURCHASE_REQUEST_CREATE", PermissionGroup.INVENTORY, mPurchaseRequest);
        Permission aPurchaseRequestU = pAct("Sửa yêu cầu mua", "PURCHASE_REQUEST_UPDATE", PermissionGroup.INVENTORY, mPurchaseRequest);
        Permission aPurchaseRequestA = pAct("Duyệt yêu cầu mua", "PURCHASE_REQUEST_APPROVE", PermissionGroup.INVENTORY, mPurchaseRequest);
        Permission aPurchaseRequestD = pAct("Xóa yêu cầu mua", "PURCHASE_REQUEST_DELETE", PermissionGroup.INVENTORY, mPurchaseRequest);

        Permission mBanner = pMod("Quản lý banner", "BANNER", PermissionGroup.SETTING);
        Permission aBannerV = pAct("Xem banner", "BANNER_VIEW", PermissionGroup.SETTING, mBanner);
        Permission aBannerC = pAct("Tạo banner", "BANNER_CREATE", PermissionGroup.SETTING, mBanner);
        Permission aBannerE = pAct("Sửa banner", "BANNER_EDIT", PermissionGroup.SETTING, mBanner);
        Permission aBannerD = pAct("Xóa banner", "BANNER_DELETE", PermissionGroup.SETTING, mBanner);

        Permission mBlog = pMod("Quản lý blog", "BLOG", PermissionGroup.SETTING);
        Permission aBlogV = pAct("Xem blog", "BLOG_VIEW", PermissionGroup.SETTING, mBlog);
        Permission aBlogC = pAct("Tạo blog", "BLOG_CREATE", PermissionGroup.SETTING, mBlog);
        Permission aBlogE = pAct("Sửa blog", "BLOG_EDIT", PermissionGroup.SETTING, mBlog);
        Permission aBlogD = pAct("Xóa blog", "BLOG_DELETE", PermissionGroup.SETTING, mBlog);

        Permission mSet = pMod("Cài đặt hệ thống", "SETTING", PermissionGroup.SETTING);
        Permission aSetV = pAct("Xem cài đặt", "SETTING_VIEW", PermissionGroup.SETTING, mSet);
        Permission aSetU = pAct("Cập nhật cài đặt", "SETTING_UPDATE", PermissionGroup.SETTING, mSet);

        Permission mChat = pMod("Chat với khách hàng", "CHAT", PermissionGroup.COMMUNICATION);
        Permission aChatV = pAct("Xem hội thoại chat", "CHAT_VIEW", PermissionGroup.COMMUNICATION, mChat);
        Permission aChatM = pAct("Quản lý chat (ghim, phân công)", "CHAT_MANAGE", PermissionGroup.COMMUNICATION, mChat);
        Permission mCustomerAdvisor = pMod("Tư vấn khách hàng", "CUSTOMER_ADVISOR", PermissionGroup.COMMUNICATION);
        Permission aCustomerAdvisorUse = pAct(
                "Sử dụng workspace tư vấn khách hàng",
                "CUSTOMER_ADVISOR_USE",
                PermissionGroup.COMMUNICATION,
                mCustomerAdvisor);

        Set<Permission> superAdminPermissions = Set.of(
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
                aPurchaseRequestV, aPurchaseRequestC, aPurchaseRequestU, aPurchaseRequestA, aPurchaseRequestD,
                aCusV, aCusC, aCusU, aCusD,
                aVouV, aVouC, aVouU, aVouD,
                aSupV, aSupC, aSupU, aSupD,
                aOrdV, aOrdC, aOrdU, aOrdD, aOrdCnf, aOrdShip, aOrdX, aOrdDone, aOrdRefund, aOrdExport,
                aBannerV, aBannerC, aBannerE, aBannerD,
                aBlogV, aBlogC, aBlogE, aBlogD,
                aSetV, aSetU,
                aChatV, aChatM,
                aCustomerAdvisorUse);

        Role superAdminRole = saveRole("SUPER_ADMIN", "Siêu quản trị", true, superAdminPermissions);

        // Chỉ bootstrap 1 tài khoản gốc để đăng nhập lần đầu; các vai trò khác
        // sẽ được quản trị viên tự tạo trong màn quản lý vai trò.
        ensureUser(
                "superadmin@agrishrimp.vn",
                "Super Admin",
                "0901000001",
                "123456",
                superAdminRole,
                UserStatus.ACTIVE);

        if (hasExistingRoles) {
            log.info(">>> ĐỒNG BỘ DỮ LIỆU NỀN TẢNG HOÀN TẤT.");
        } else {
            log.info(">>> KHỞI TẠO DỮ LIỆU NỀN TẢNG HOÀN TẤT.");
        }
    }

    private Permission pMod(String name, String code, PermissionGroup group) {
        return permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(Permission.builder()
                        .name(name)
                        .code(code)
                        .groupName(group)
                        .type(PermissionType.MODULE)
                        .build()));
    }

    private Permission pAct(String name, String code, PermissionGroup group, Permission parent) {
        return permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(Permission.builder()
                        .name(name)
                        .code(code)
                        .groupName(group)
                        .type(PermissionType.ACTION)
                        .parentId(parent.getId())
                        .build()));
    }

    private Role saveRole(String slug, String displayName, boolean isSystem, Set<Permission> permissions) {
        return roleRepository.findBySlug(slug)
                .map(existingRole -> {
                    existingRole.setDisplayName(displayName);
                    existingRole.setIsSystem(isSystem);
                    existingRole.setIsActive(true);
                    existingRole.setDescription("Vai trò " + displayName);
                    existingRole.getPermissions().clear();
                    existingRole.getPermissions().addAll(permissions);
                    return roleRepository.save(existingRole);
                })
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .slug(slug)
                        .displayName(displayName)
                        .isSystem(isSystem)
                        .isActive(true)
                        .description("Vai trò " + displayName)
                        .permissions(new HashSet<>(permissions))
                        .build()));
    }

    private void ensureUser(
            String email,
            String fullName,
            String phoneNumber,
            String rawPassword,
            Role role,
            UserStatus status) {
        userRepository.findByEmail(email)
                .map(existingUser -> {
                    existingUser.setFullName(fullName);
                    existingUser.setPhoneNumber(phoneNumber);
                    existingUser.setStatus(status);
                    existingUser.setRole(role);
                    existingUser.setProvider(AuthProvider.LOCAL);
                    existingUser.setGender(Gender.MALE);
                    existingUser.setDateOfBirth(LocalDate.of(1985, 3, 15));
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .fullName(fullName)
                        .email(email)
                        .phoneNumber(phoneNumber)
                        .passwordHash(passwordEncoder.encode(rawPassword))
                        .status(status)
                        .role(role)
                        .gender(Gender.MALE)
                        .dateOfBirth(LocalDate.of(1985, 3, 15))
                        .provider(AuthProvider.LOCAL)
                        .build()));
    }
}

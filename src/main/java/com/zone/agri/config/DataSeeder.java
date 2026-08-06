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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        Permission aRptInvAllBranches = pAct(
                "Xem báo cáo kho mọi chi nhánh",
                "REPORT_INVENTORY_VIEW_ALL_BRANCHES",
                PermissionGroup.REPORT,
                mRpt);
        Permission aRptFin = pAct("Báo cáo tài chính", "REPORT_FINANCE_VIEW", PermissionGroup.REPORT, mRpt);
        Permission aRptFinAllBranches = pAct(
                "Xem báo cáo tài chính mọi chi nhánh",
                "REPORT_FINANCE_VIEW_ALL_BRANCHES",
                PermissionGroup.REPORT,
                mRpt);

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

        Permission mDriver = pMod("Quản lý tài xế", "DRIVER", PermissionGroup.INVENTORY);
        Permission aDriverV = pAct("Xem tài xế", "DRIVER_VIEW", PermissionGroup.INVENTORY, mDriver);
        Permission aDriverC = pAct("Thêm tài xế", "DRIVER_CREATE", PermissionGroup.INVENTORY, mDriver);
        Permission aDriverU = pAct("Sửa tài xế", "DRIVER_UPDATE", PermissionGroup.INVENTORY, mDriver);
        Permission aDriverD = pAct("Xóa tài xế", "DRIVER_DELETE", PermissionGroup.INVENTORY, mDriver);

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
        Permission aBlogA = pAct("Duyệt blog", "BLOG_APPROVE", PermissionGroup.SETTING, mBlog);

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

        Permission mAgronomistWorkspace = pMod("Workspace kỹ sư nông nghiệp", "AGRONOMIST_WORKSPACE", PermissionGroup.AI_KNOWLEDGE);
        Permission aAgronomistWorkspaceUse = pAct(
                "Sử dụng workspace kỹ sư nông nghiệp",
                "AGRONOMIST_WORKSPACE_USE",
                PermissionGroup.AI_KNOWLEDGE,
                mAgronomistWorkspace);
        Permission mAiKnowledge = pMod("Tri thức AI doctor", "AI_KNOWLEDGE", PermissionGroup.AI_KNOWLEDGE);
        Permission aAiKnowledgeView = pAct("Xem tri thức AI", "AI_KNOWLEDGE_VIEW", PermissionGroup.AI_KNOWLEDGE, mAiKnowledge);
        Permission aAiKnowledgeCreate = pAct("Tạo tri thức AI", "AI_KNOWLEDGE_CREATE", PermissionGroup.AI_KNOWLEDGE, mAiKnowledge);
        Permission aAiKnowledgeUpdate = pAct("Cập nhật tri thức AI", "AI_KNOWLEDGE_UPDATE", PermissionGroup.AI_KNOWLEDGE, mAiKnowledge);
        Permission aAiKnowledgeApprove = pAct("Duyệt tri thức AI", "AI_KNOWLEDGE_APPROVE", PermissionGroup.AI_KNOWLEDGE, mAiKnowledge);
        Permission aAiImportKnowledge = pAct("Import tri thức AI", "AI_IMPORT_KNOWLEDGE", PermissionGroup.AI_KNOWLEDGE, mAiKnowledge);
        Permission aAiCaseReview = pAct("Xử lý case AI", "AI_CASE_REVIEW", PermissionGroup.AI_KNOWLEDGE, mAiKnowledge);

        Set<Permission> superAdminPermissions = Set.of(
                aDashV, aWspaceV,
                aRptSale, aRptInv, aRptInvAllBranches, aRptFin, aRptFinAllBranches,
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
                aDriverV, aDriverC, aDriverU, aDriverD,
                aOrdV, aOrdC, aOrdU, aOrdD, aOrdCnf, aOrdShip, aOrdX, aOrdDone, aOrdRefund, aOrdExport,
                aBannerV, aBannerC, aBannerE, aBannerD,
                aBlogV, aBlogC, aBlogE, aBlogD, aBlogA,
                aSetV, aSetU,
                aChatV, aChatM,
                aCustomerAdvisorUse,
                aAgronomistWorkspaceUse,
                aAiKnowledgeView, aAiKnowledgeCreate, aAiKnowledgeUpdate,
                aAiKnowledgeApprove, aAiImportKnowledge, aAiCaseReview);

        Role superAdminRole = saveRole("SUPER_ADMIN", "Siêu quản trị", true, superAdminPermissions);
        Role adminRole = saveRole("ADMIN", "Quản trị viên", true, superAdminPermissions);
        // Role mac dinh cho luong dang ky tai khoan khach hang trong AuthService.signup().
        // Khong gan permission quan tri de user moi khong truy cap duoc cac workspace noi bo.
        saveRole("USER", "Người dùng", true, Set.of());

        ensureUser(
                "superadmin@agrishrimp.vn",
                "Super Admin",
                "0901000001",
                "123456",
                superAdminRole,
                UserStatus.ACTIVE);
        ensureUser(
                "admin@agrishrimp.vn",
                "Admin",
                "0901000002",
                "123456",
                adminRole,
                UserStatus.ACTIVE);

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
                    if (existingRole.getPermissions() == null) {
                        existingRole.setPermissions(new HashSet<>());
                    } else {
                        existingRole.getPermissions().clear();
                    }
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
        findBootstrapUser(email, phoneNumber, role.getSlug())
                .map(existingUser -> {
                    existingUser.setFullName(fullName);
                    if (!userRepository.existsByEmailAndIdNot(email, existingUser.getId())) {
                        existingUser.setEmail(email);
                    } else {
                        log.warn("Seeder giữ nguyên email hiện tại cho user id={} vì email {} đang thuộc user khác",
                                existingUser.getId(), email);
                    }
                    if (!userRepository.existsByPhoneNumberAndIdNot(phoneNumber, existingUser.getId())) {
                        existingUser.setPhoneNumber(phoneNumber);
                    } else {
                        log.warn("Seeder giữ nguyên số điện thoại hiện tại cho user id={} vì số {} đang thuộc user khác",
                                existingUser.getId(), phoneNumber);
                    }
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

    private Optional<User> findBootstrapUser(String email, String phoneNumber, String roleSlug) {
        return userRepository.findByEmail(email)
                .or(() -> userRepository.findByPhoneNumber(phoneNumber))
                .or(() -> userRepository.findFirstByRole_SlugOrderByIdAsc(roleSlug));
    }
}


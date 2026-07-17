package com.zone.agri.common;

import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.SignInRequiredException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WarehouseContext {

    private static final Set<String> WAREHOUSE_AUTHORITIES = Set.of(
            "SUPPLIER_VIEW",
            "SUPPLIER_CREATE",
            "SUPPLIER_UPDATE",
            "SUPPLIER_DELETE",
            "IMPORT_VIEW",
            "IMPORT_CREATE",
            "IMPORT_UPDATE",
            "IMPORT_APPROVE",
            "IMPORT_CANCEL",
            "IMPORT_DELETE",
            "EXPORT_VIEW",
            "EXPORT_CREATE",
            "EXPORT_UPDATE",
            "EXPORT_APPROVE",
            "EXPORT_CANCEL",
            "EXPORT_DELETE",
            "TRANSFER_VIEW",
            "TRANSFER_CREATE",
            "TRANSFER_UPDATE",
            "TRANSFER_APPROVE",
            "TRANSFER_CANCEL",
            "TRANSFER_DELETE",
            "INVENTORY_CHECK_VIEW",
            "INVENTORY_CHECK_CREATE",
            "INVENTORY_CHECK_UPDATE",
            "INVENTORY_CHECK_APPROVE",
            "INVENTORY_CHECK_CANCEL",
            "INVENTORY_CHECK_DELETE",
            "PURCHASE_REQUEST_VIEW",
            "PURCHASE_REQUEST_CREATE",
            "PURCHASE_REQUEST_UPDATE",
            "PURCHASE_REQUEST_APPROVE",
            "PURCHASE_REQUEST_DELETE");

    /**
     * Tài khoản có quyền kho nhưng không gắn chi nhánh → null (xem toàn hệ thống)
     * Tài khoản có quyền kho và gắn chi nhánh → bị scope theo chi nhánh đó
     * Tài khoản không có quyền kho / chưa đăng nhập → throws 403
     */
    public Long resolveWarehouseId() {
        UserDetail user = AuthUtils.getUserDetail();
        if (user == null) {
            throw new SignInRequiredException("Vui lòng đăng nhập");
        }

        boolean hasWarehousePermission = WAREHOUSE_AUTHORITIES.stream()
                .anyMatch(AuthUtils::hasAuthority);
        if (!hasWarehousePermission) {
            throw new Forbidden("Không có quyền truy cập kho");
        }

        return user.getBranchId();
    }

    /**
     * Nếu user đang bị scope theo một chi nhánh thì không được truy cập kho khác.
     */
    public void assertAccess(Long targetWarehouseId) {
        Long allowed = resolveWarehouseId();
        if (allowed != null && !allowed.equals(targetWarehouseId)) {
            throw new Forbidden("Không được phép truy cập kho khác");
        }
    }

    public boolean isSuperAdmin() {
        UserDetail user = AuthUtils.getUserDetail();
        return user != null
                && user.getBranchId() == null
                && WAREHOUSE_AUTHORITIES.stream().anyMatch(AuthUtils::hasAuthority);
    }
}

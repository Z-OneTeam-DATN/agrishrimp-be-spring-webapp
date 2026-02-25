package com.zone.agri.common;

import com.zone.agri.dto.user.UserDetail;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.SignInRequiredException;
import org.springframework.stereotype.Component;

@Component
public class WarehouseContext {

    /**
     * SUPER_ADMIN → null (no branch filter, sees all)
     * BRANCH_MANAGER → their branchId
     * USER / unauthenticated → throws 403
     */
    public Long resolveWarehouseId() {
        UserDetail user = AuthUtils.getUserDetail();
        if (user == null) {
            throw new SignInRequiredException("Vui lòng đăng nhập");
        }

        String slug = (user.getRole() != null) ? user.getRole().getSlug().toUpperCase() : "USER";

        return switch (slug) {
            // Thêm ADMIN vào đây
            case "ADMIN" -> null;
            case "BRANCH_MANAGER" -> {
                Long bid = user.getBranchId();
                if (bid == null) throw new Forbidden("BRANCH_MANAGER chưa được gán chi nhánh");
                yield bid;
            }
            default -> throw new Forbidden("Không có quyền truy cập kho");
        };
    }

    /**
     * SUPER_ADMIN always passes.
     * BRANCH_MANAGER throws 403 if targetWarehouseId != their branchId.
     */
    public void assertAccess(Long targetWarehouseId) {
        Long allowed = resolveWarehouseId();
        if (allowed != null && !allowed.equals(targetWarehouseId)) {
            throw new Forbidden("Không được phép truy cập kho khác");
        }
    }

    public boolean isSuperAdmin() {
        UserDetail user = AuthUtils.getUserDetail();
        if (user == null || user.getRole() == null) return false;
        return "ADMIN".equals(user.getRole().getSlug());
    }
}

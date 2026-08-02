package com.zone.agri.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ActivityLogCatalog {

    private static final Map<String, String> MODULE_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> ACTION_LABELS = new LinkedHashMap<>();
    private static final Set<String> MUTATION_ACTIONS = Set.of(
            "CREATE", "UPDATE", "DELETE", "APPROVE", "CANCEL",
            "CONFIRM", "COMPLETE", "SHIP", "REFUND", "IMPORT");

    static {
        MODULE_LABELS.put("PURCHASE_REQUEST", "Yêu cầu mua hàng");
        MODULE_LABELS.put("IMPORT", "Nhập hàng");
        MODULE_LABELS.put("EXPORT", "Xuất kho");
        MODULE_LABELS.put("INVENTORY_CHECK", "Kiểm kê kho");
        MODULE_LABELS.put("TRANSFER", "Điều chuyển kho");
        MODULE_LABELS.put("SUPPLIER", "Nhà cung cấp");
        MODULE_LABELS.put("PRODUCT", "Sản phẩm");
        MODULE_LABELS.put("CATEGORY", "Danh mục");
        MODULE_LABELS.put("ATTRIBUTE", "Thuộc tính");
        MODULE_LABELS.put("ORDER", "Đơn hàng");
        MODULE_LABELS.put("CUSTOMER", "Khách hàng");
        MODULE_LABELS.put("VOUCHER", "Khuyến mãi");
        MODULE_LABELS.put("STAFF", "Nhân sự");
        MODULE_LABELS.put("BRANCH", "Chi nhánh");
        MODULE_LABELS.put("ROLE", "Vai trò & quyền");
        MODULE_LABELS.put("BANNER", "Banner");
        MODULE_LABELS.put("BLOG", "Blog");
        MODULE_LABELS.put("AI_KNOWLEDGE", "AI Doctor");

        ACTION_LABELS.put("CREATE", "Thêm mới");
        ACTION_LABELS.put("UPDATE", "Cập nhật");
        ACTION_LABELS.put("DELETE", "Xóa");
        ACTION_LABELS.put("APPROVE", "Duyệt");
        ACTION_LABELS.put("CANCEL", "Hủy");
        ACTION_LABELS.put("CONFIRM", "Xác nhận");
        ACTION_LABELS.put("COMPLETE", "Hoàn tất");
        ACTION_LABELS.put("SHIP", "Giao hàng");
        ACTION_LABELS.put("REFUND", "Hoàn tiền");
        ACTION_LABELS.put("IMPORT", "Import dữ liệu");
    }

    private ActivityLogCatalog() {
    }

    public static Map<String, String> moduleLabels() {
        return MODULE_LABELS;
    }

    public static String moduleLabel(String module) {
        return MODULE_LABELS.getOrDefault(module, module);
    }

    public static String actionLabel(String action) {
        return ACTION_LABELS.getOrDefault(action, action);
    }

    public static Optional<PermissionActivity> fromPermissionCode(String permissionCode, String httpMethod) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return Optional.empty();
        }

        String normalized = permissionCode.trim().toUpperCase();
        String action = resolveAction(normalized, httpMethod);
        if (action == null || !MUTATION_ACTIONS.contains(action)) {
            return Optional.empty();
        }

        String module = normalized;
        if ("EDIT".equals(action)) {
            module = stripSuffix(module, "_EDIT");
            action = "UPDATE";
        } else {
            module = stripSuffix(module, "_" + action);
        }

        if ("AI_IMPORT_KNOWLEDGE".equals(normalized)) {
            module = "AI_KNOWLEDGE";
            action = "IMPORT";
        }

        if (!MODULE_LABELS.containsKey(module)) {
            return Optional.empty();
        }

        return Optional.of(new PermissionActivity(module, action));
    }

    private static String resolveAction(String permissionCode, String httpMethod) {
        if (permissionCode.endsWith("_CREATE")) {
            return "CREATE";
        }
        if (permissionCode.endsWith("_UPDATE") || permissionCode.endsWith("_EDIT")) {
            return permissionCode.endsWith("_EDIT") ? "EDIT" : "UPDATE";
        }
        if (permissionCode.endsWith("_DELETE")) {
            return "DELETE";
        }
        if (permissionCode.endsWith("_APPROVE")) {
            return "APPROVE";
        }
        if (permissionCode.endsWith("_CANCEL")) {
            return "CANCEL";
        }
        if (permissionCode.endsWith("_CONFIRM")) {
            return "CONFIRM";
        }
        if (permissionCode.endsWith("_COMPLETE")) {
            return "COMPLETE";
        }
        if (permissionCode.endsWith("_SHIP")) {
            return "SHIP";
        }
        if (permissionCode.endsWith("_REFUND")) {
            return "REFUND";
        }
        if ("AI_IMPORT_KNOWLEDGE".equals(permissionCode)) {
            return "IMPORT";
        }
        return null;
    }

    private static String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix)
                ? value.substring(0, value.length() - suffix.length())
                : value;
    }

    public record PermissionActivity(String module, String action) {
    }
}

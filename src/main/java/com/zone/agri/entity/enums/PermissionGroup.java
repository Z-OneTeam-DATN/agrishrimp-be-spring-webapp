package com.zone.agri.entity.enums;

public enum PermissionGroup {
    SYSTEM("Hệ thống"),                      // Gồm: Tổng quan, Bàn làm việc kho
    ADMINISTRATION("Quản trị"),              // Gồm: Nhân viên hệ thống, Chi nhánh & Kho
    PRODUCT_CATALOG("Hàng hóa"),             // Gồm: Sản phẩm, Danh mục, Thuộc tính
    INVENTORY_TRANSACTION("Giao dịch kho"),  // Gồm: Nhập hàng, Xuất hàng, Điều chuyển, Kiểm kê
    SHIPPING("Vận chuyển"),                  // Gồm: Tổng quan vận chuyển
    PARTNER("Đối tác"),                      // Gồm: Nhà cung cấp, Khách hàng
    REPORT("Báo cáo"),                       // Gồm: Báo cáo bán hàng, Báo cáo kho, Báo cáo tài chính
    SETTING("Cài đặt");                      // Gồm: Cài đặt (Phần Footer)

    private final String displayName;

    PermissionGroup(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

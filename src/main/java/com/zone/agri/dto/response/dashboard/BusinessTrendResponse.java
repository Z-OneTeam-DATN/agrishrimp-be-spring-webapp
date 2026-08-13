package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Chuỗi số liệu kinh doanh theo cột (ngày hoặc tháng) cho biểu đồ cột ở trang tổng quan.
 *
 * Khác với sales-performance (cố định 7 ngày, chỉ doanh thu/lợi nhuận), chuỗi này trả thêm giá vốn
 * và số đơn theo từng mốc để vẽ được cụm cột Doanh thu / Giá vốn / Lợi nhuận giống báo cáo tài chính.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessTrendResponse {

    /** DAY | MONTH — độ chi tiết THỰC TẾ đã dùng, có thể khác yêu cầu nếu khoảng chọn quá dài. */
    private String granularity;

    /** Nhãn mô tả khoảng đang vẽ, ví dụ "6 tháng gần nhất". */
    private String rangeLabel;

    private List<Point> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        /** Khoá mốc: "2026-08" khi theo tháng, "2026-08-11" khi theo ngày. */
        private String period;

        /** Nhãn hiển thị đã Việt hoá: "T8/2026" hoặc "11/08". */
        private String label;

        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private long orders;
    }
}

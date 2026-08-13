package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private Long totalCustomers;
    private Long totalProducts;

    // Các trường phẳng dưới đây giữ nguyên cho client cũ; client mới nên đọc *Change để biết
    // thêm giá trị kỳ trước và cờ comparable (xem MetricChangeResponse).
    private double revenueChangePercent;
    private boolean revenueIsNew;
    private double ordersChangePercent;
    private boolean ordersIsNew;
    private double customersChangePercent;
    private boolean customersIsNew;

    private MetricChangeResponse revenueChange;
    private MetricChangeResponse ordersChange;
    private MetricChangeResponse customersChange;
}

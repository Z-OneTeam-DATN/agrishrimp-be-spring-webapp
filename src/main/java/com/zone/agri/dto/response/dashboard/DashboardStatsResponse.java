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


package com.zone.agri.dto.response.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportSummaryResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private Long branchId;
    private String branchName;
    private RevenueSection revenue;
    private DeliverySection delivery;
    private ReturnSection returns;
    private PaymentSection payment;
    private OrderSection orders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueSection {
        private BigDecimal totalRevenue;
        private BigDecimal totalProfit;
        private long totalOrders;
        private List<TrendPoint> trend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliverySection {
        private long totalShipments;
        private List<BreakdownItem> breakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnSection {
        private long totalReturnedOrders;
        private BigDecimal totalReturnedAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSection {
        private BigDecimal paidAmount;
        private long paidOrders;
        private long unpaidOrders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSection {
        private long totalOrders;
        private long totalProductsSold;
        private BigDecimal averageOrderValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private LocalDate date;
        private BigDecimal revenue;
        private BigDecimal profit;
        private long orderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakdownItem {
        private String key;
        private String label;
        private long count;
    }
}

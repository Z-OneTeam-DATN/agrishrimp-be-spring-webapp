package com.zone.agri.dto.response.supplier;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDebtInsightResponse {
    private boolean insufficientData;
    private BigDecimal totalOutstandingDebt;
    private BigDecimal totalDebtChangeVsPreviousPeriod; // null nếu không so sánh
    private List<SupplierRankingItem> supplierRanking;
    private List<StaffDebtSummaryItem> staffDebtSummary;
    private List<BreakdownItem> breakdown;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierRankingItem {
        private Long supplierId;
        private String supplierCode;
        private String supplierName;
        private String phone;
        private BigDecimal totalDebt;
        private double weightedAvgDebtAge; // số ngày
        private String ageStatus; // "NORMAL" | "WARNING" | "CRITICAL"
        private double priorityScore;
        private int priorityRank;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffDebtSummaryItem {
        private Long staffId;
        private String staffName;
        private BigDecimal totalDebtFromOrders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakdownItem {
        private String factor; // "TOTAL_DEBT" | "AGE_DISTRIBUTION" | "TREND"
        private BigDecimal value;
        private String note;
    }
}

package com.zone.agri.dto.response.financial;

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
public class ProfitLossInsightResponse {
    private Object netProfitChangePercent; // String "NO_PREVIOUS_DATA" hoặc Double
    private List<ContributionBreakdownItem> contributionBreakdown;
    private double cogsRatio;
    private String cogsRatioStatus; // "NORMAL" | "WARNING"
    private double returnRatio;
    private String returnRatioStatus; // "NORMAL" | "WARNING"
    private boolean isNetProfitNegative;
    private List<String> excludedZeroFields;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributionBreakdownItem {
        private String factor; // "REVENUE" | "COGS" | "SHIPPING" | "DISCOUNT" | "RETURNS"
        private BigDecimal currentValue;
        private BigDecimal previousValue; // null nếu không có kỳ trước
        private BigDecimal changeAmount; // null nếu không có kỳ trước
        private String note;
    }
}

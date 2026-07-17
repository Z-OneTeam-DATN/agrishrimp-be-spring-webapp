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
    private BigDecimal cogsRatio;
    private String cogsRatioStatus;
    private BigDecimal returnRatio;
    private String returnRatioStatus;
    private String netProfitChangePercent;
    private List<ProfitLossContributionItemResponse> contributionBreakdown;
    private List<String> warnings;
}

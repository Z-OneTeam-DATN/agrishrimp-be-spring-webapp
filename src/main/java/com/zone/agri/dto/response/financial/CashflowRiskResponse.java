package com.zone.agri.dto.response.financial;

import java.math.BigDecimal;
import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashflowRiskResponse {
    private String riskLevel; // "SAFE", "WARNING", "CRITICAL"
    private BigDecimal currentBalance;
    private BigDecimal expectedInflow;
    private BigDecimal totalDebtDueInWindow;
    private BigDecimal projectedBalance;
    private BigDecimal shortfallAmount;
    private List<PrioritizedDebtDto> prioritizedDebts;
    private List<BreakdownItemDto> breakdown;
    private List<String> warnings;
    private boolean insufficientData;
}

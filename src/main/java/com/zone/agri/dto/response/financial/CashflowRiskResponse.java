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
    // Số ngày cửa sổ dự phóng nợ đến hạn thật sự dùng để tính totalDebtDueInWindow — frontend
    // cần số này để hiển thị nhãn đúng (trước đây hardcode "14 ngày" dù cấu hình có thể khác).
    private int windowDays;
    private List<String> warnings;
    private boolean insufficientData;
}

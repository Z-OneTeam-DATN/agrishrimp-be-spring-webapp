package com.zone.agri.dto.response.financial;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashbookSummaryResponse {
    private BigDecimal openingBalance;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal closingBalance;
}

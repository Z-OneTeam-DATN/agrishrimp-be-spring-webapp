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
public class ProfitLossContributionItemResponse {
    private String factor;
    private BigDecimal currentValue;
    private BigDecimal previousValue;
    private BigDecimal changeAmount;
    private String note;
}

package com.zone.agri.dto.response.financial;

import java.math.BigDecimal;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakdownItemDto {
    private String factor; // "OPENING_BALANCE", "EXPECTED_INFLOW", "DEBT_DUE_IN_WINDOW"
    private BigDecimal value;
    private String note;
}

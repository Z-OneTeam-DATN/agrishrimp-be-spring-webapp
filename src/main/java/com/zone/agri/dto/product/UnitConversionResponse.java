package com.zone.agri.dto.product;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class UnitConversionResponse {
    private Long id;
    private String fromUnit;
    private String toUnit;
    private BigDecimal rate;
}

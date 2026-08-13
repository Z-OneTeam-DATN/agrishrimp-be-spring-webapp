package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricChangeResponse {

    private BigDecimal current;

    private BigDecimal previous;

    private BigDecimal changeAmount;

    private double changePercent;

    private boolean comparable;

    private boolean newBaseline;

    private boolean negativeBaseline;

    private String direction;
}


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
public class MonthlyBusinessResultsResponse {
    private String yearMonth;

    private BigDecimal currentMonthRevenue;
    private BigDecimal previousMonthRevenue;
    private double revenueChangePercent;
    private boolean revenueIsNew;

    private BigDecimal currentMonthProfit;
    private BigDecimal previousMonthProfit;
    private double profitChangePercent;
    private boolean profitIsNew;

    private long currentMonthOrders;
    private long previousMonthOrders;
    private double orderChangePercent;
    private boolean orderIsNew;
}

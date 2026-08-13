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
public class DailyBusinessResultsResponse {
    private BigDecimal todayRevenue;
    private BigDecimal yesterdayRevenue;
    private double revenueChangePercent;
    private boolean revenueIsNew;

    private BigDecimal todayProfit;
    private BigDecimal yesterdayProfit;
    private double profitChangePercent;
    private boolean profitIsNew;

    private long todayOrders;
    private long yesterdayOrders;
    private double orderChangePercent;
    private boolean orderIsNew;

    private MetricChangeResponse revenueChange;
    private MetricChangeResponse profitChange;
    private MetricChangeResponse orderChange;

    private long deliveredOrders;
    private long returnedOrders;
    private long cancelledOrders;
    private MetricChangeResponse deliveredChange;
    private MetricChangeResponse returnedChange;
    private MetricChangeResponse cancelledChange;
}


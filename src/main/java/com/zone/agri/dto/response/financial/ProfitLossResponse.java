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
public class ProfitLossResponse {
    private BigDecimal grossRevenue;
    private BigDecimal returnedGoods;
    private BigDecimal vat;
    private BigDecimal shippingFeeCollected;
    private BigDecimal shippingFeeReturned;
    private BigDecimal discount;
    private BigDecimal discountReturned;
    private BigDecimal netProductRevenue;
    private BigDecimal netRevenue;

    private BigDecimal cogs;
    private BigDecimal pointPayment;
    private BigDecimal shippingFeePaid;
    private BigDecimal grossProfit;

    private BigDecimal otherIncome;
    private BigDecimal customerReturnFee;
    private BigDecimal otherExpenses;
    private BigDecimal netProfit;
}

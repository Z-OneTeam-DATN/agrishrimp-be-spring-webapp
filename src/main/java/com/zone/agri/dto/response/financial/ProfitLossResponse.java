package com.zone.agri.dto.response.financial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitLossResponse {
    private BigDecimal revenue;               // 1a. Tiền hàng bán ra
    private BigDecimal returnedGoods;         // 1b. Tiền hàng trả lại
    private BigDecimal vat;                   // 2. Thuế VAT
    private BigDecimal shippingFeeCollected;  // 3. Phí giao hàng thu của khách
    private BigDecimal discount;              // 4. Chiết khấu

    private BigDecimal cogs;                  // II-1. Chi phí giá vốn hàng hóa
    private BigDecimal pointPayment;          // II-2. Thanh toán bằng điểm
    private BigDecimal shippingFeePaid;       // II-3. Phí giao hàng trả đối tác

    private BigDecimal otherIncome;           // III-1. Phiếu thu khác (Thu nhập khác)
    private BigDecimal customerReturnFee;     // III-2. Phí khách trả hàng

    private BigDecimal otherExpenses;         // IV-1. Phiếu chi khác (Chi phí khác)
}

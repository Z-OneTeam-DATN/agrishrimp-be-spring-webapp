package com.zone.agri.dto.response.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfirmOrderResponse {
    private Long orderId;
    private String orderCode;
    private String status;
    private String voucherCode;
    private List<SubOrderSummaryDto> subOrders;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalShippingFee;
    private String checkoutUrl; // non-null only for PAYOS orders
}

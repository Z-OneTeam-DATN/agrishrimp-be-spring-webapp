package com.zone.agri.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrderSummaryDto {
    private Long subOrderId;
    private Long branchId;
    private String branchName;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private String estimatedDays;
    private String carrier;
    private String carrierOrderId;
}

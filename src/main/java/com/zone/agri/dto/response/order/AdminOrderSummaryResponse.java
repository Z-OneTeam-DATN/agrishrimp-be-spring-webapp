package com.zone.agri.dto.response.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminOrderSummaryResponse {
    private long totalOrders;
    private long shortageOrders;
    private long unpaidOrders;
    private BigDecimal totalValue;
}

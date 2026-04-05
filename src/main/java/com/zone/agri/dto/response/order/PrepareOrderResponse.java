package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepareOrderResponse {
    /** Token để dùng trong /confirm — lưu trong Redis 30 phút */
    private String prepareToken;
    /** true nếu toàn bộ giỏ hàng có thể thực hiện */
    private Boolean canFulfill;
    private String voucherCode;
    private List<SubOrderDraftDto> subOrders;
    private BigDecimal totalSubtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalShippingFee;
    private BigDecimal totalAmount;
    private List<OutOfStockItemDto> outOfStockItems;
}

package com.zone.agri.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lưu tạm trong Redis sau /prepare.
 * Dùng để validate và tạo đơn trong /confirm.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepareOrderDraft {
    private String prepareToken;
    private Long userId;
    private Double userLat;
    private Double userLng;
    private String deliveryAddress;
    private Integer deliveryDistrictId;
    private Integer deliveryProvinceId;
    private String deliveryWardCode;
    private List<SubOrderDraftDto> subOrders;
    private List<OutOfStockItemDto> outOfStockItems;
    private BigDecimal totalSubtotal;
    private BigDecimal totalShippingFee;
    private BigDecimal totalAmount;
}

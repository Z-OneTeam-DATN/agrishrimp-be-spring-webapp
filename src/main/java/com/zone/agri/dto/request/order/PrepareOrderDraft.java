package com.zone.agri.dto.request.order;

import com.zone.agri.dto.response.order.CartItemDto;
import com.zone.agri.dto.response.order.OrderItemDto;
import com.zone.agri.dto.response.order.OutOfStockItemDto;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.dto.response.order.SuggestedTransferDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepareOrderDraft {
    private String prepareToken;
    private Long userId;
    private Long addressId;
    private String voucherCode;
    private String stockStatus;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    private Long branchId;
    private List<OrderItemDto> finalItems;
    private List<SuggestedTransferDto> suggestedTransfers;

    private List<CartItemDto> cartItems;
    private String receiverName;
    private String receiverPhone;
    private Double userLat;
    private Double userLng;
    private String deliveryAddress;
    private Integer deliveryDistrictId;
    private Integer deliveryProvinceId;
    private String deliveryWardCode;
    private List<SubOrderDraftDto> subOrders;
    private List<OutOfStockItemDto> outOfStockItems;
    private BigDecimal totalSubtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalShippingFee;
    private BigDecimal totalAmount;
}

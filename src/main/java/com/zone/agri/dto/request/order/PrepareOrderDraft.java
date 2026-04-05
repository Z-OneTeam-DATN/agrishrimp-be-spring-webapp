package com.zone.agri.dto.request.order;

import com.zone.agri.dto.response.order.CartItemDto;
import com.zone.agri.dto.response.order.OrderItemDto;
import com.zone.agri.dto.response.order.OutOfStockItemDto;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
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
    private String voucherCode;

    // --- Thông tin bổ sung theo yêu cầu ---
    private Long branchId; // Chi nhánh chính hoặc chi nhánh duy nhất
    private List<OrderItemDto> finalItems; // Danh sách hàng hóa cuối cùng đã phân bổ
    // --------------------------------------

    private List<CartItemDto> cartItems; // Snapshot gio hang goc de confirm tinh lai quote cuoi

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

package com.zone.agri.dto.response.order;

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
public class PrepareOrderResponse {
    private String prepareToken;
    private LocalDateTime expiresAt;
    private Boolean canFulfill;
    private Boolean canPlaceOrder;
    private Boolean requiresManualApproval;
    private String voucherCode;
    private String stockStatus;
    private PreparePrimaryBranchDto primaryBranch;
    private List<SuggestedTransferDto> suggestedTransfers;
    private List<SubOrderDraftDto> subOrders;
    private BigDecimal totalSubtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalShippingFee;
    private BigDecimal totalAmount;
    private List<OutOfStockItemDto> outOfStockItems;
}

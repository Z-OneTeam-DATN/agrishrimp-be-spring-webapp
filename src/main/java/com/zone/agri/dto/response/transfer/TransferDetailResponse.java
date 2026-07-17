package com.zone.agri.dto.response.transfer;

import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferDetailResponse {
    private Long id;
    private String transferCode;
    private String transferType;
    private InventoryTransferStatus status;
    private String description;
    private String vehicle;
    private String transporter;
    private String dispatchOrder;
    private String referenceCode;
    private LocalDateTime createdAt;
    private Long sourceBranchId;
    private Long destinationBranchId;
    private String fromBranchName;
    private String toBranchName;
    private Long createdByBranchId;
    private String createdByBranchName;
    private String createdByName;
    private String sourceConfirmedByName;
    private LocalDateTime sourceConfirmedAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String shippedByName;
    private LocalDateTime shippedAt;
    private String inspectionStartedByName;
    private LocalDateTime inspectionStartedAt;
    private String receivedByName;
    private LocalDateTime receivedAt;
    private String settledByName;
    private LocalDateTime settledAt;
    private Integer totalQuantity;
    private BigDecimal totalValue;
    private List<ItemDetail> items;

    private TransferBusinessType transferBusinessType;
    private BigDecimal transferAmount;
    private TransferSettlementStatus settlementStatus;
    private BigDecimal sourceReceivableAmount;
    private BigDecimal destPayableAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private BigDecimal requiredMarginPercent;

    @Data
    @Builder
    public static class ItemDetail {
        private Long variantId;
        private String productName;
        private String sku;
        private String unit;
        private Integer quantityRequested;
        private Integer quantityReal;
        private Integer quantityAccepted;
        private Integer quantityRejected;
        private String note;
        private BigDecimal unitTransferPrice;
        private BigDecimal totalTransferPrice;
    }
}

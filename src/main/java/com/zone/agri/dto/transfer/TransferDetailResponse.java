package com.zone.agri.dto.transfer;

import com.zone.agri.entity.enums.InventoryTransferStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private String fromBranchName;
    private String toBranchName;
    private Integer totalQuantity;
    private BigDecimal totalValue;
    private List<ItemDetail> items; // Danh sách hàng hóa chi tiết

    @Data
    @Builder
    public static class ItemDetail {
        private Long variantId;
        private String productName;
        private String sku;
        private String unit;
        private Integer quantityRequested;
        private Integer quantityReal;
        private String note;
    }
}
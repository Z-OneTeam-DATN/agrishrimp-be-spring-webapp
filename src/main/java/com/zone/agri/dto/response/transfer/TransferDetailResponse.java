package com.zone.agri.dto.response.transfer;

import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
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
    private BigDecimal totalValue; // Tổng giá trị theo giá vốn FIFO (quản trị kho)
    private List<ItemDetail> items; // Danh sách hàng hóa chi tiết

    // --- THÔNG TIN BÁN NỘI BỘ ---
    private TransferBusinessType transferBusinessType; // STOCK_TRANSFER hoặc INTERNAL_SALE
    private BigDecimal transferAmount;                 // Tổng thành tiền nội bộ (chỉ có khi INTERNAL_SALE)
    private TransferSettlementStatus settlementStatus; // Trạng thái thanh toán nội bộ
    private BigDecimal sourceReceivableAmount;         // Phải thu nội bộ của kho xuất
    private BigDecimal destPayableAmount;              // Phải trả nội bộ của kho nhận

    @Data
    @Builder
    public static class ItemDetail {
        private Long variantId;
        private String productName;
        private String sku;
        private String unit;
        private Integer quantityRequested; // Expected
        private Integer quantityReal;      // Actual
        private Integer quantityAccepted;  // Accepted
        private Integer quantityRejected;  // Rejected
        private String note;
        private BigDecimal unitTransferPrice;  // Đơn giá nội bộ (chỉ có khi INTERNAL_SALE)
        private BigDecimal totalTransferPrice; // Thành tiền nội bộ = unitTransferPrice × quantityRequested
    }
}

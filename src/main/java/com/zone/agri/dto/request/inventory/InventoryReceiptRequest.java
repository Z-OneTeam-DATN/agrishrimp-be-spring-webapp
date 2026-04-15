package com.zone.agri.dto.request.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class InventoryReceiptRequest {
    @NotBlank(message = "Loại nhập không được để trống")
    private String importType;

    private Long sourceBranchId;

    private String receiptCode;
    private String supplierCode;
    private String branchName;
    private String deliverer;
    private String creator; // FE sends "creator"
    private String entryDate;
    private String note;
    private String importStatus;

    private List<String> tags;
    private BigDecimal paymentAmount;

    // Liên kết với Phiếu yêu cầu mua (tùy chọn)
    private Long purchaseRequestId;

    @NotEmpty(message = "Phiếu nhập phải có ít nhất 1 sản phẩm")
    @Valid
    private List<ItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {
        @NotBlank(message = "Mã sản phẩm không được để trống")
        private String productCode; // SKU của variant

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        private Integer plannedQuantity; // Expected Qty

        private Integer quantityReal;   // Actual Qty (QC fill)
        private Integer quantityAccepted; // Accepted Qty (QC fill)
        private Integer quantityRejected; // Rejected Qty (QC fill)

        private String lotNumber;
        private String expiryDate;
        private BigDecimal importPrice;
        private BigDecimal newSellingPrice;
    }
}

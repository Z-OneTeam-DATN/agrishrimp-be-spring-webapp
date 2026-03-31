package com.zone.agri.dto.response.product;

import com.zone.agri.entity.enums.VariantStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductVariantResponse {
    private Long id;
    private String sku;
    private String barcode;
    private String productName;
    private BigDecimal price;
    private BigDecimal importPrice;
    private String imageUrl;
    private VariantStatus status;
    private Integer quantity;
    private List<BatchInfoDto> batches;

    private List<AttributeValueResponse> attributeValues;

    // --- CẤU TRÚC LÔ HÀNG ---
    @Data
    @Builder
    public static class BatchInfoDto {
        private Long inventoryId;
        private String branchName;     // Tên chi nhánh giữ lô này
        private String batchNumber;    // Số lô (từ Inventory Note Detail)
        private Integer quantity;      // Tồn kho hiện tại của lô này
        private BigDecimal importPrice;// Giá vốn (Chỉ Admin/Manager mới có data này, NV sẽ null)
        private BigDecimal sellingPrice; // Giá bán (Auto = importPrice * Hệ số lợi nhuận)
        private String expiryDate;     // Ngày hết hạn (ISO-8601 string)
    }
}

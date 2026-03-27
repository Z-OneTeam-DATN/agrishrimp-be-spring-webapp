package com.zone.agri.dto.response.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryNoteDetailResponse {
    private Long id;
    private Long productVariantId;
    private String sku;
    private String productName;
    private String name; // Alias cho productName theo yêu cầu FE
    private String unit;
    private Integer quantity;           // Số lượng hệ thống (dùng cho Kiểm Kho)
    private Integer systemQuantity;     // Alias cho quantity theo yêu cầu FE
    private Integer quantityRequested;  // Số lượng yêu cầu (dùng cho Xuất Kho)
    private Integer quantityReal;       // Số lượng thực tế (dùng cho cả Xuất và Kiểm)
    private BigDecimal price;
    private String batchNumber;
    private String imageUrl;
    private String note; // Lý do trả hàng hoặc ghi chú kiểm kho
}

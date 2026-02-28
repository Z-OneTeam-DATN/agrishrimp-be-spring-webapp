package com.zone.agri.dto.response;

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
    private String unit;
    private Integer quantityRequested;
    private BigDecimal price;
    private String imageUrl;
    private String note; // Lý do trả hàng (nếu có)
}
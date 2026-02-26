package com.zone.agri.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class InventoryNoteDetailResponse {
    private Long id;
    private Long productVariantId; // Thêm dòng này
    private String sku;
    private String productName;
    private String unit;
    private Integer quantityRequested;
    private BigDecimal price;
    private String imageUrl;
    private String note;
}
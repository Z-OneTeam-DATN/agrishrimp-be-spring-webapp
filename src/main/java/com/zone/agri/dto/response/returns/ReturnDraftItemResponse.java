package com.zone.agri.dto.response.returns;

import com.zone.agri.entity.enums.ReturnItemSourceType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReturnDraftItemResponse {
    private ReturnItemSourceType sourceType;
    private Long sourceItemId;
    private Long productVariantId;
    private Long subOrderId;
    private Long branchId;
    private String branchName;
    private String productName;
    private String variantName;
    private String sku;
    private String image;
    private Integer orderedQuantity;
    private Integer maxReturnQuantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}

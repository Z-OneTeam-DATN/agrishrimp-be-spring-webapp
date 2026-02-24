package com.zone.agri.dto.product;

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
    private BigDecimal costPrice;
    private BigDecimal price;
    private BigDecimal wholesalePrice;
    private Integer quantity;
    private BigDecimal shippingWeight;
    private String unit;
    private String imageUrl;
    private VariantStatus status;
    private List<AttributeValueResponse> attributeValues;
    private List<UnitConversionResponse> unitConversions;
}

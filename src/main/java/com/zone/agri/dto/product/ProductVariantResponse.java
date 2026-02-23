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
    private String formulation;
    private String packaging;
    private String unit;
    private BigDecimal importPrice;
    private BigDecimal price;
    private BigDecimal wholesalePrice;
    private Integer quantity;
    private BigDecimal weightValue;
    private String netWeightUnit;
    private BigDecimal shippingWeight;
    private String imageUrl;
    private VariantStatus status;
    private List<UnitConversionResponse> unitConversions;
}

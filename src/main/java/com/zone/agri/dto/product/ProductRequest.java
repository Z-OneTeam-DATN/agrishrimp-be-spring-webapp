package com.zone.agri.dto.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRequest {
    private String name;
    private Long categoryId;
    private String brand;
    private String origin;
    private String description;
    private String status;
    private List<String> images;
    private List<VariantDto> variants;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VariantDto {
        private String sku;
        private String barcode;
        private BigDecimal costPrice;
        private BigDecimal price;
        private BigDecimal wholesalePrice;
        private Long initialStock;
        private BigDecimal shippingWeight;
        private String image;
        
        /** Danh sách ID của các giá trị thuộc tính (Màu sắc, Dạng bào chế, Khối lượng, v.v.) */
        private List<Long> attributeValueIds;
    }
}
        
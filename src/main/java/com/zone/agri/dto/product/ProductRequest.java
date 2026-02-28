package com.zone.agri.dto.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
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
        private String image;

        /** Danh sách ID của các giá trị thuộc tính (Kích thước, Màu sắc, v.v.) */
        private List<Long> attributeValueIds;
    }
}
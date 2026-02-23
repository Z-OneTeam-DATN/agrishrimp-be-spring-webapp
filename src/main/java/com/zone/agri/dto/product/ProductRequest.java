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
    private String baseSku;
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
        private Integer initialStock;
        private BigDecimal netWeight;
        private String netWeightUnit;
        private BigDecimal shippingWeight;
        private String image;
        private String customSpecs;

        // DÙNG LIST NÀY ĐỂ NHẬN BAO NHIÊU THUỘC TÍNH TỪ UI CŨNG ĐƯỢC
        private List<AttributeDto> attributes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributeDto {
        private String name;  // Tên thuộc tính (VD: "Màu sắc", "Dạng bào chế")
        private String value; // Giá trị (VD: "Đỏ", "Viên nén")
    }
}
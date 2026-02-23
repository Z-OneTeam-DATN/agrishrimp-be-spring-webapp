package com.zone.agri.dto.product;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CreateProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String status;
    private String origin;
    private String baseSku;
    private String categoryName;
    private String brandName;
    private List<String> imageUrls;
    private List<ProductVariantResponse> variants;
}

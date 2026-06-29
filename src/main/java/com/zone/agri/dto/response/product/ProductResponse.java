package com.zone.agri.dto.response.product;

import com.zone.agri.dto.response.admin.CategoryDTO;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String shortDesc;
    private String description;
    private String status;
    private Long supplierId;
    private String supplierName;
    private String baseSku;
    private String categoryName;
    private Long soldCount;
    private Float ratingAverage;
    private Integer reviewCount;
    private CategoryDTO category;

    // Tồn kho động (tính tổng từ các kho/lô hàng)
    private Integer inventory;

    private List<String> imageUrls;
    private List<ProductVariantResponse> variants;
}

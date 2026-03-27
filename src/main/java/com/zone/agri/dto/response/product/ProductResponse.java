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
    private String description;
    private String status;
    private String origin;
    private String baseSku;
    private String categoryName;
    private String brandName;
    private CategoryDTO category;
    private BrandResponse brand;

    // Tồn kho động (tính tổng từ các kho/lô hàng)
    private Integer inventory;

    private List<String> imageUrls;
    private List<ProductVariantResponse> variants;
}

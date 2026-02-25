package com.zone.agri.dto.product;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO cho API công khai (website bán hàng).
 * KHÔNG chứa bất kỳ dữ liệu nội bộ nào:
 * costPrice, importPrice, quantity chi tiết, warehouseId, v.v.
 */
@Data
@Builder
public class PublicProductResponse {

    private Long id;
    private String name;
    private String slug;
    private String shortDesc;
    private String description;
    private String origin;
    private String baseSku;

    /** Danh mục — chỉ trả id + name, không trả status hay dữ liệu nội bộ */
    private CategoryInfo category;

    private String brandName;

    /** Danh sách URL ảnh sản phẩm chính */
    private List<String> imageUrls;

    /** Chỉ các variant có status = ACTIVE */
    private List<PublicVariantResponse> variants;

    /**
     * true  → tổng tồn kho toàn hệ thống = 0 (hết hàng)
     * false → còn hàng
     * Không trả số lượng chi tiết hay phân chia theo kho.
     */
    private Boolean isOutOfStock;

    // -------------------------------------------------------------------------

    @Data
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
    }
}

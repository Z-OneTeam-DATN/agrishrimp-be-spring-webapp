package com.zone.agri.dto.miniapp.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Thông tin sản phẩm gửi sang AI generate-prescription.
 *
 * Phase BE-3: bổ sung field {@code category} để AI biết phân loại
 * thuốc/chế phẩm (kháng sinh, men vi sinh, khoáng chất…),
 * giúp AI chọn related_product_ids chính xác hơn.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiStockItem {

    /** ID sản phẩm trong hệ thống — AI phải trả lại đúng id này */
    private Long id;

    /** Tên sản phẩm */
    private String name;

    /**
     * Tên danh mục (ví dụ: "Kháng sinh", "Men vi sinh", "Khoáng chất").
     * Giúp AI phân loại đúng nhóm sản phẩm khi tạo prescription.
     */
    private String category;

    /** Liều dùng — null ở Phase BE-3 */
    private String dosage;

    /** Số lượng tồn — null ở Phase BE-3, tránh gây nhiễu AI */
    private Integer quantity;

    /** Ghi chú thêm — null ở Phase BE-3 */
    private String notes;
}

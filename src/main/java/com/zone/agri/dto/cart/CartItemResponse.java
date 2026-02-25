package com.zone.agri.dto.cart;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class CartItemResponse {
    private Long id; // ID của dòng trong giỏ hàng
    private Long variantId; // ID của phân loại sản phẩm
    private String name; // Tên sản phẩm
    private String variant; // Tên phân loại (VD: 500g/túi) - Giữ nguyên nếu cũ
    private String categoryName; // Tên danh mục
    private String brandName;    // Tên thương hiệu
    private String productForm;  // Loại sản phẩm (Dạng bột, lỏng...)
    private String variantName;  // Tên đầy đủ của phân loại (Màu sắc: Đỏ, Size: L)
    private BigDecimal price; // Đơn giá
    private Integer quantity; // Số lượng trong giỏ
    private Integer stock; // Tồn kho hiện tại để validate
    private String image; // Ảnh sản phẩm
}
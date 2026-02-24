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
    private String variant; // Tên phân loại (VD: 500g/túi)
    private BigDecimal price; // Đơn giá
    private Integer quantity; // Số lượng trong giỏ
    private Integer stock; // Tồn kho hiện tại để validate
    private String image; // Ảnh sản phẩm
}
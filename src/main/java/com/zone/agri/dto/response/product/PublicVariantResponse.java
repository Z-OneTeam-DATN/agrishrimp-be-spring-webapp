package com.zone.agri.dto.response.product;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Biến thể sản phẩm dành cho API công khai.
 * Các trường bị loại bỏ so với ProductVariantResponse (internal):
 *  - costPrice / importPrice   (giá nhập — bí mật nội bộ)
 *  - quantity                  (tồn kho chi tiết — không lộ ra ngoài)
 *  - status                    (chỉ ACTIVE mới được trả về — filter trước)
 *  - imagePublicId             (Cloudinary internal ID)
 */
@Data
@Builder
public class PublicVariantResponse {

    private Long id;
    private String sku;
    private String barcode;

    /** Giá bán lẻ */
    private BigDecimal price;

    /** Giá sỉ (hiển thị nếu có) */
    private BigDecimal wholesalePrice;

    /** Trọng lượng vận chuyển (gram / kg tùy quy ước) */
    private BigDecimal shippingWeight;

    /** Đơn vị tính cơ bản (VD: "Hộp", "Gói", "Kg") */
    private String unit;

    private String imageUrl;

    /** Các giá trị thuộc tính động (màu, size, v.v.) */
    private List<AttributeValueResponse> attributeValues;

    /** Bảng quy đổi đơn vị */
    private List<UnitConversionResponse> unitConversions;
}

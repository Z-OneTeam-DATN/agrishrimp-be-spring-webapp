package com.zone.agri.dto.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VariantRequest {

    @NotBlank(message = "Mã SKU biến thể không được để trống")
    @Size(max = 50, message = "Mã SKU không được vượt quá 50 ký tự")
    private String sku;

    @Size(max = 50, message = "Mã vạch không được vượt quá 50 ký tự")
    private String barcode;

    @NotBlank(message = "Dạng bào chế không được để trống")
    @Size(max = 100, message = "Dạng bào chế không được vượt quá 100 ký tự")
    private String formulation;

    @NotBlank(message = "Quy cách đóng gói không được để trống")
    @Size(max = 100, message = "Quy cách đóng gói không được vượt quá 100 ký tự")
    private String packaging;

    @NotBlank(message = "Đơn vị tính nhỏ nhất không được để trống")
    @Size(max = 50, message = "Đơn vị tính không được vượt quá 50 ký tự")
    private String unit;

    @NotNull(message = "Giá vốn không được để trống")
    @DecimalMin(value = "0", message = "Giá vốn phải >= 0")
    private BigDecimal costPrice;

    @NotNull(message = "Giá bán lẻ không được để trống")
    @DecimalMin(value = "0", message = "Giá bán lẻ phải >= 0")
    private BigDecimal price;

    @DecimalMin(value = "0", message = "Giá bán sỉ phải >= 0")
    private BigDecimal wholesalePrice;

    @Min(value = 0, message = "Tồn kho khởi tạo phải >= 0")
    private Integer initialStock;

    @DecimalMin(value = "0", message = "Khối lượng tịnh phải >= 0")
    private BigDecimal netWeight;

    private String netWeightUnit;

    @DecimalMin(value = "0", message = "Trọng lượng vận chuyển phải >= 0")
    private BigDecimal shippingWeight;

    /** Các thuộc tính bổ sung tuỳ chọn (VD: Màu sắc, Kích cỡ) */
    @Valid
    private List<AttributeDto> attributes;

    /** Bảng quy đổi đơn vị cho biến thể này */
    @Valid
    private List<UnitConversionRequest> unitConversions;
}

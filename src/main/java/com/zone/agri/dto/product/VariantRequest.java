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

    @DecimalMin(value = "0", message = "Trọng lượng vận chuyển phải >= 0")
    private BigDecimal shippingWeight;

    /** Danh sách ID của các giá trị thuộc tính (Màu sắc, Dạng bào chế, Khối lượng, v.v.) */
    private List<Long> attributeValueIds;

    /** Bảng quy đổi đơn vị cho biến thể này */
    @Valid
    private List<UnitConversionRequest> unitConversions;
}

package com.zone.agri.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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

    private String image;

    private String imageUrl;
    private BigDecimal shippingWeight;

    /** Danh sách ID của các giá trị thuộc tính (Phân loại) */
    @NotEmpty(message = "Biến thể phải có ít nhất 1 thuộc tính")
    private List<Long> attributeValueIds;

}

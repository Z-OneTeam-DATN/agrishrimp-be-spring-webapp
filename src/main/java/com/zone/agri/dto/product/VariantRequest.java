package com.zone.agri.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class VariantRequest {

    @NotBlank(message = "Mã SKU biến thể không được để trống")
    @Size(max = 50, message = "Mã SKU không được vượt quá 50 ký tự")
    private String sku;

    @Size(max = 50, message = "Mã vạch không được vượt quá 50 ký tự")
    private String barcode;

    /** Danh sách ID của các giá trị thuộc tính (Phân loại) */
    private List<Long> attributeValueIds;

}
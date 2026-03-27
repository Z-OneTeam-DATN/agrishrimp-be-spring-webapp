package com.zone.agri.dto.request.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255, message = "Tên sản phẩm không được vượt quá 255 ký tự")
    private String name;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    @Size(max = 100, message = "Thương hiệu không được vượt quá 100 ký tự")
    private String brand;

    @Size(max = 100, message = "Xuất xứ không được vượt quá 100 ký tự")
    private String origin;

    @Size(max = 50, message = "Mã SKU gốc không được vượt quá 50 ký tự")
    private String baseSku;

    /** HTML từ rich text editor – cho phép chứa tags */
    private String description;

    @Pattern(
        regexp = "DRAFT|ACTIVE|INACTIVE",
        message = "Trạng thái phải là một trong: DRAFT, ACTIVE, INACTIVE"
    )
    private String status;

    @NotEmpty(message = "Sản phẩm phải có ít nhất 1 biến thể")
    @Valid
    private List<VariantRequest> variants;
}

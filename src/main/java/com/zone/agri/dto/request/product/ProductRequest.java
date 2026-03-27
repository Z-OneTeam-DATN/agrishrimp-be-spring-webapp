package com.zone.agri.dto.request.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 255, message = "Tên sản phẩm không được vượt quá 255 ký tự")
    private String name;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    @NotBlank(message = "Thương hiệu không được để trống")
    @Size(max = 100, message = "Thương hiệu không được vượt quá 100 ký tự")
    private String brand;

    @Size(max = 100, message = "Xuất xứ không được vượt quá 100 ký tự")
    private String origin;

    private String description;

    @NotBlank(message = "Trạng thái không được để trống")
    private String status;

    private List<String> images;

    @NotEmpty(message = "Sản phẩm phải có ít nhất 1 biến thể")
    @Valid
    private List<VariantDto> variants;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VariantDto {
        @NotBlank(message = "Mã SKU biến thể không được để trống")
        @Size(max = 50, message = "Mã SKU không được vượt quá 50 ký tự")
        private String sku;

        @Size(max = 50, message = "Mã vạch không được vượt quá 50 ký tự")
        private String barcode;

        private String image;

        /** Danh sách ID của các giá trị thuộc tính (Kích thước, Màu sắc, v.v.) */
        @NotEmpty(message = "Biến thể phải có ít nhất 1 thuộc tính")
        private List<Long> attributeValueIds;
    }
}

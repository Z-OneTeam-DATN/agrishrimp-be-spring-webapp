package com.zone.agri.dto.product;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeDto {
    @NotBlank(message = "Tên thuộc tính không được để trống")
    private String name;  // Tên thuộc tính (VD: "Màu sắc")

    @NotBlank(message = "Giá trị thuộc tính không được để trống")
    private String value; // Giá trị (VD: "Đỏ")
}

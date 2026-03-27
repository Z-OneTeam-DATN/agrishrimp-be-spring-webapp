package com.zone.agri.dto.request.product;

import com.zone.agri.entity.enums.CategoryStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryPost {
    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    private String imageUrl;

    private CategoryStatus status;

    private Long parentId;
}

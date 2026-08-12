package com.zone.agri.dto.response.admin;

import com.zone.agri.entity.enums.CategoryStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryDTO {
    private Long id;

    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    private String imageUrl;
    private CategoryStatus status;
    private Long parentId;
    private String parentName;

    private Long productCount;
}

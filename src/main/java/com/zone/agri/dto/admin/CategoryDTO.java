package com.zone.agri.dto.admin;

import com.zone.agri.entity.enums.CategoryStatus;
import lombok.Data;

@Data
public class CategoryDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private CategoryStatus status;
    private Long parentId;
    private String parentName;

    private Long productCount;
}
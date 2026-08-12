package com.zone.agri.dto.response.product;

import com.zone.agri.entity.enums.CategoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private String imageUrl;
    private CategoryStatus status;
    private Long parentId;
    private String parentName;
    private Long productCount;
}

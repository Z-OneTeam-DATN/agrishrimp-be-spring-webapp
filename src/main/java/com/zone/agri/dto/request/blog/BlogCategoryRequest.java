package com.zone.agri.dto.request.blog;

import lombok.Data;

@Data
public class BlogCategoryRequest {
    private String name;
    private String slug;
    private String description;
    private String status;
}

package com.zone.agri.dto.response.blog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BlogCategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long postCount;
}

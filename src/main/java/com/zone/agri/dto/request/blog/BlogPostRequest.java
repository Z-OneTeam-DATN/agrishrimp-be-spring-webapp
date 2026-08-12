package com.zone.agri.dto.request.blog;

import lombok.Data;

import java.util.List;

@Data
public class BlogPostRequest {
    private String title;
    private String slug;
    private String excerpt;
    private String content;
    private String thumbnailUrl;
    private String thumbnailPublicId;
    private String status;       // DRAFT | PUBLISHED
    private Long categoryId;
    private List<Long> tagIds;
    private List<Long> productIds;
}

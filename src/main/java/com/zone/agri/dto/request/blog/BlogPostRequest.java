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
    private String seoTitle;
    private String metaDescription;
    private String canonicalUrl;
    private String focusKeyword;
    private String coverImageAlt;
    private String status;       // DRAFT | IN_REVIEW | PUBLISHED
    private Long categoryId;
    private List<Long> tagIds;
    private List<Long> productIds;
}

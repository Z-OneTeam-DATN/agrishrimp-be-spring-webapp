package com.zone.agri.dto.response.blog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BlogPostResponse {

    private Long id;
    private String title;
    private String slug;
    private String excerpt;
    private String content;
    private String thumbnailUrl;
    private String status;
    private Long viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private AuthorInfo author;
    private CategoryInfo category;
    private List<TagInfo> tags;
    private List<RelatedProductInfo> relatedProducts;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuthorInfo {
        private Long id;
        private String fullName;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryInfo {
        private Long id;
        private String name;
        private String slug;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TagInfo {
        private Long id;
        private String name;
        private String slug;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RelatedProductInfo {
        private Long id;
        private String name;
        private String slug;
        private String imageUrl;
        private Double basePrice;
    }
}

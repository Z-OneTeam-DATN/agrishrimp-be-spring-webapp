package com.zone.agri.controller;

import com.zone.agri.dto.response.blog.BlogCategoryResponse;
import com.zone.agri.dto.request.blog.BlogCommentRequest;
import com.zone.agri.dto.response.blog.BlogCommentResponse;
import com.zone.agri.dto.response.blog.BlogPostResponse;
import com.zone.agri.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/blog")
@RequiredArgsConstructor
@Tag(name = "Public Blog APIs", description = "Không yêu cầu xác thực")
public class PublicBlogController {

    private final BlogService blogService;

    @Operation(summary = "Danh sách bài viết đã xuất bản")
    @GetMapping("/posts")
    public ResponseEntity<Page<BlogPostResponse>> getPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 9, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(blogService.getPublished(keyword, categoryId, pageable));
    }

    @Operation(summary = "Chi tiết bài viết theo slug (tăng lượt xem)")
    @GetMapping("/posts/{slug}")
    public ResponseEntity<BlogPostResponse> getPost(@PathVariable String slug) {
        return ResponseEntity.ok(blogService.getPublicBySlug(slug, true));
    }

    @Operation(summary = "Danh sách bình luận bài viết")
    @GetMapping("/posts/{slug}/comments")
    public ResponseEntity<List<BlogCommentResponse>> getComments(@PathVariable String slug) {
        return ResponseEntity.ok(blogService.getPublicComments(slug));
    }

    @Operation(summary = "Gửi bình luận bài viết")
    @PostMapping("/posts/{slug}/comments")
    public ResponseEntity<BlogCommentResponse> createComment(
            @PathVariable String slug,
            @Valid @RequestBody BlogCommentRequest request) {
        return ResponseEntity.ok(blogService.createPublicComment(slug, request));
    }

    @Operation(summary = "Danh sách danh mục blog")
    @GetMapping("/categories")
    public ResponseEntity<List<BlogCategoryResponse>> getCategories() {
        return ResponseEntity.ok(blogService.getPublicCategories());
    }
}

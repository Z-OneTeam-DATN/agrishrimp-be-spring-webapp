package com.zone.agri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.request.blog.BlogCategoryRequest;
import com.zone.agri.dto.request.blog.BlogPostRejectRequest;
import com.zone.agri.dto.request.blog.BlogPostRequest;
import com.zone.agri.dto.request.blog.BlogTagRequest;
import com.zone.agri.dto.response.blog.BlogCategoryResponse;
import com.zone.agri.dto.response.blog.BlogPostResponse;
import com.zone.agri.dto.response.common.ApiResponse;
import com.zone.agri.entity.BlogPost;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.BlogService;
import com.zone.agri.service.ShrimpPriceBlogAutomationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
@Tag(name = "Admin Blog Management")
@SecurityRequirement(name = "bearerAuth")
public class AdminBlogController {

    private final BlogService blogService;
    private final ShrimpPriceBlogAutomationService shrimpPriceBlogAutomationService;
    private final ObjectMapper objectMapper;

    // ─── CATEGORIES ────────────────────────────────────────────────────────────

    @GetMapping("/categories")
    @RequirePermission("BLOG_VIEW")
    public ResponseEntity<ApiResponse<List<BlogCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(blogService.getAllCategories(), "OK"));
    }

    @GetMapping("/tags")
    @RequirePermission("BLOG_VIEW")
    public ResponseEntity<ApiResponse<List<BlogPostResponse.TagInfo>>> getTags() {
        return ResponseEntity.ok(ApiResponse.success(blogService.getAllTags(), "OK"));
    }

    @GetMapping("/authors")
    @RequirePermission("BLOG_VIEW")
    public ResponseEntity<ApiResponse<List<BlogPostResponse.AuthorInfo>>> getAuthors() {
        return ResponseEntity.ok(ApiResponse.success(blogService.getAllAuthors(), "OK"));
    }

    @PostMapping("/tags")
    @RequirePermission({"BLOG_CREATE", "BLOG_EDIT"})
    public ResponseEntity<ApiResponse<BlogPostResponse.TagInfo>> createTag(@RequestBody BlogTagRequest req) {
        return ResponseEntity.ok(ApiResponse.success(blogService.createTag(req), "Tạo tag thành công"));
    }

    @PostMapping("/categories")
    @RequirePermission("BLOG_CREATE")
    public ResponseEntity<ApiResponse<BlogCategoryResponse>> createCategory(@RequestBody BlogCategoryRequest req) {
        return ResponseEntity.ok(ApiResponse.success(blogService.createCategory(req), "Tạo danh mục thành công"));
    }

    @PutMapping("/categories/{id}")
    @RequirePermission("BLOG_EDIT")
    public ResponseEntity<ApiResponse<BlogCategoryResponse>> updateCategory(
            @PathVariable Long id, @RequestBody BlogCategoryRequest req) {
        return ResponseEntity.ok(ApiResponse.success(blogService.updateCategory(id, req), "Cập nhật danh mục thành công"));
    }

    @DeleteMapping("/categories/{id}")
    @RequirePermission("BLOG_DELETE")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        blogService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa danh mục"));
    }

    // ─── POSTS ─────────────────────────────────────────────────────────────────

    @GetMapping("/posts")
    @RequirePermission("BLOG_VIEW")
    public ResponseEntity<ApiResponse<Page<BlogPostResponse>>> getAllPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long authorId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                blogService.getAll(keyword, status, categoryId, authorId, pageable), "OK"));
    }

    @GetMapping("/posts/{id}")
    @RequirePermission("BLOG_VIEW")
    public ResponseEntity<ApiResponse<BlogPostResponse>> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(blogService.getById(id), "OK"));
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("BLOG_CREATE")
    public ResponseEntity<ApiResponse<BlogPostResponse>> createPost(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) throws Exception {
        BlogPostRequest req = objectMapper.readValue(dataJson, BlogPostRequest.class);
        return ResponseEntity.ok(ApiResponse.success(blogService.create(req, thumbnail), "Tạo bài viết thành công"));
    }

    @PutMapping(value = "/posts/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("BLOG_EDIT")
    public ResponseEntity<ApiResponse<BlogPostResponse>> updatePost(
            @PathVariable Long id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) throws Exception {
        BlogPostRequest req = objectMapper.readValue(dataJson, BlogPostRequest.class);
        return ResponseEntity.ok(ApiResponse.success(blogService.update(id, req, thumbnail), "Cập nhật bài viết thành công"));
    }

    @PatchMapping("/posts/{id}/publish")
    @RequirePermission("BLOG_EDIT")
    public ResponseEntity<ApiResponse<Void>> publish(@PathVariable Long id) {
        blogService.changeStatus(id, "PUBLISHED");
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xuất bản bài viết"));
    }

    @PatchMapping("/posts/{id}/draft")
    @RequirePermission("BLOG_EDIT")
    public ResponseEntity<ApiResponse<Void>> draft(@PathVariable Long id) {
        blogService.changeStatus(id, "DRAFT");
        return ResponseEntity.ok(ApiResponse.success(null, "Đã chuyển về nháp"));
    }

    @PatchMapping("/posts/{id}/approve")
    @RequirePermission("BLOG_APPROVE")
    public ResponseEntity<ApiResponse<Void>> approvePost(@PathVariable Long id) {
        blogService.approve(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã duyệt và xuất bản bài viết"));
    }

    @PatchMapping("/posts/{id}/reject")
    @RequirePermission("BLOG_APPROVE")
    public ResponseEntity<ApiResponse<Void>> rejectPost(
            @PathVariable Long id, @RequestBody(required = false) BlogPostRejectRequest req) {
        blogService.reject(id, req != null ? req.getReason() : null);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã từ chối, chuyển về nháp"));
    }

    @DeleteMapping("/posts/{id}")
    @RequirePermission("BLOG_DELETE")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable Long id) {
        blogService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa bài viết"));
    }

    @PostMapping("/automations/shrimp-price/run")
    @RequirePermission("BLOG_APPROVE")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runShrimpPriceBlogAutomation() {
        BlogPost post;
        try {
            post = shrimpPriceBlogAutomationService.createDailyShrimpPriceBlogDraftNow();
        } catch (Exception ex) {
            throw new BadRequestException("Không tạo được bài giá tôm miền Tây: " + rootErrorMessage(ex));
        }
        return ResponseEntity.ok(ApiResponse.success(
                Map.of(
                        "id", post.getId(),
                        "slug", post.getSlug(),
                        "status", post.getStatus() != null ? post.getStatus().name() : "",
                        "title", post.getTitle()
                ),
                "Đã tạo bài giá tôm miền Tây và gửi admin duyệt"));
    }

    private String rootErrorMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}

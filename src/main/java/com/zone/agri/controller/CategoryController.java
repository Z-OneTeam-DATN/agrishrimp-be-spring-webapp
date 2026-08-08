package com.zone.agri.controller;

import com.zone.agri.dto.request.product.CategoryPost;
import com.zone.agri.dto.response.admin.CategoryDTO;
import com.zone.agri.dto.response.common.ApiResponse;
import com.zone.agri.dto.response.product.CategoryResponse;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Product Categories", description = "Quản lý danh mục, nhóm hàng hóa và phân loại sản phẩm")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Lấy tất cả danh mục", description = "Có hỗ trợ tìm kiếm và lọc")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CATEGORY_VIEW")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CategoryStatus status) {

        List<CategoryDTO> dtos = categoryService.getAllCategories(keyword, status);
        List<CategoryResponse> data = dtos.stream().map(this::mapToResponse).collect(Collectors.toList());

        ApiResponse<List<CategoryResponse>> response = ApiResponse.success(data, "Lấy danh sách danh mục thành công");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xem chi tiết danh mục", description = "Lấy thông tin chi tiết của một nhóm hàng.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CATEGORY_VIEW")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(
            @Parameter(description = "ID danh mục cần xem", example = "5")
            @PathVariable Long id) {

        CategoryDTO dto = categoryService.getCategoryById(id);
        CategoryResponse data = mapToResponse(dto);

        ApiResponse<CategoryResponse> response = ApiResponse.success(data, "Lấy thông tin danh mục thành công");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Tạo danh mục mới", description = "Thêm mới một nhóm hàng hóa vào hệ thống.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CATEGORY_CREATE")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryPost categoryPost, Errors errors) {

        ApiResponse<CategoryResponse> response = new ApiResponse<>();

        // BẮT BUỘC: Kiểm tra lỗi validate đầu hàm
        if (errors.hasErrors()) {
            String error = errors.getAllErrors().get(0).getDefaultMessage();
            response.setMessage(error);
            // Tuyệt đối KHÔNG dùng ok() cho trường hợp lỗi
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            CategoryDTO inputDto = mapToServiceDto(categoryPost);
            CategoryDTO savedDto = categoryService.createCategory(inputDto);
            CategoryResponse data = mapToResponse(savedDto);

            response = ApiResponse.success(data, "Tạo danh mục thành công");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (com.zone.agri.exception.ConflictException e) {
            response.setMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Operation(summary = "Cập nhật danh mục", description = "Chỉnh sửa tên, mô tả hoặc trạng thái danh mục.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CATEGORY_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @Parameter(description = "ID danh mục cần sửa", example = "5")
            @PathVariable Long id,
            @Valid @RequestBody CategoryPost categoryPost, Errors errors) {

        ApiResponse<CategoryResponse> response = new ApiResponse<>();

        if (errors.hasErrors()) {
            String error = errors.getAllErrors().get(0).getDefaultMessage();
            response.setMessage(error);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            CategoryDTO inputDto = mapToServiceDto(categoryPost);
            CategoryDTO updatedDto = categoryService.updateCategory(id, inputDto);
            CategoryResponse data = mapToResponse(updatedDto);

            response = ApiResponse.success(data, "Cập nhật danh mục thành công");
            return ResponseEntity.ok(response);

        } catch (com.zone.agri.exception.NotFoundException e) {
            response.setMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (com.zone.agri.exception.ConflictException e) {
            response.setMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Operation(summary = "Xóa danh mục", description = "Xóa vĩnh viễn một danh mục.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CATEGORY_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "ID danh mục cần xóa", example = "10")
            @PathVariable Long id) {

        ApiResponse<Void> response = new ApiResponse<>();
        try {
            categoryService.deleteCategory(id);
            response = ApiResponse.success(null, "Xóa danh mục thành công");
            return ResponseEntity.ok(response);

        } catch (com.zone.agri.exception.NotFoundException e) {
            response.setMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (com.zone.agri.exception.ConflictException e) {
            response.setMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    // --- Helper Methods ---

    private CategoryResponse mapToResponse(CategoryDTO dto) {
        return CategoryResponse.builder()
                .id(dto.getId())
                .name(dto.getName())
                .slug(dto.getSlug())
                .imageUrl(dto.getImageUrl())
                .status(dto.getStatus())
                .parentId(dto.getParentId())
                .parentName(dto.getParentName())
                .productCount(dto.getProductCount())
                .build();
    }

    private CategoryDTO mapToServiceDto(CategoryPost post) {
        CategoryDTO dto = new CategoryDTO();
        dto.setName(post.getName());
        dto.setImageUrl(post.getImageUrl());
        dto.setStatus(post.getStatus());
        dto.setParentId(post.getParentId());
        return dto;
    }
}

package com.zone.agri.controller;

import com.zone.agri.dto.product.ProductResponse;
import com.zone.agri.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page; // Import Page
import org.springframework.data.domain.Pageable; // Import Pageable
import org.springframework.data.web.PageableDefault; // Import PageableDefault
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public") // Base path for public APIs
@RequiredArgsConstructor
@Tag(name = "Public Product APIs", description = "Các API công khai để lấy thông tin sản phẩm và danh mục")
public class PublicProductController {

    private final ProductService productService;

    @Operation(summary = "Lấy danh sách sản phẩm đang hoạt động theo danh mục",
               description = "Trả về danh sách sản phẩm đang hoạt động thuộc một danh mục cụ thể. Không yêu cầu xác thực.")
    @GetMapping("/categories/{categoryId}/products")
    public ResponseEntity<List<ProductResponse>> getPublicProductsByCategory(
            @Parameter(description = "ID của danh mục", example = "1", required = true)
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.getPublicProductsByCategoryId(categoryId));
    }

    @Operation(summary = "Lấy danh sách sản phẩm đang hoạt động theo thương hiệu",
               description = "Trả về danh sách sản phẩm đang hoạt động thuộc một thương hiệu cụ thể. Không yêu cầu xác thực.")
    @GetMapping("/brands/{brandId}/products")
    public ResponseEntity<List<ProductResponse>> getPublicProductsByBrandId(
            @Parameter(description = "ID của thương hiệu", example = "1", required = true)
            @PathVariable Long brandId) {
        return ResponseEntity.ok(productService.getPublicProductsByBrandId(brandId));
    }

    @Operation(summary = "Lấy danh sách sản phẩm công khai (có phân trang và lọc)",
               description = "Trả về danh sách các sản phẩm đang hoạt động với tùy chọn tìm kiếm theo từ khóa, lọc theo danh mục và lọc theo thương hiệu. Không yêu cầu xác thực.")
    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponse>> getPublicProducts(
            @Parameter(description = "Tìm kiếm theo tên sản phẩm", example = "Tôm")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo ID danh mục", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Lọc theo ID thương hiệu", example = "1")
            @RequestParam(required = false) Long brandId, // Thêm tham số brandId
            @PageableDefault(size = 10, page = 0) Pageable pageable) {
        return ResponseEntity.ok(productService.getPublicProducts(keyword, categoryId, brandId, pageable));
    }

    @Operation(summary = "Lấy chi tiết sản phẩm theo slug",
               description = "Trả về chi tiết sản phẩm đang hoạt động dựa trên slug. Không yêu cầu xác thực.")
    @GetMapping("/products/slug/{slug}")
    public ResponseEntity<ProductResponse> getProductDetailBySlug(
            @Parameter(description = "Slug của sản phẩm", example = "thuc-an-tom-tomboy-t12", required = true)
            @PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductDetailForUser(slug));
    }
}

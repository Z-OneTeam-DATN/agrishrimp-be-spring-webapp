package com.zone.agri.controller;

import com.zone.agri.dto.request.product.RecommendationClickRequest;
import com.zone.agri.dto.response.product.FrequentlyBoughtTogetherResponse;
import com.zone.agri.dto.response.product.ProductResponse;
import com.zone.agri.dto.response.review.ReviewResponse;
import com.zone.agri.service.ProductService;
import com.zone.agri.service.ProductRecommendationService;
import com.zone.agri.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final ProductRecommendationService productRecommendationService;
    private final ReviewService reviewService;

    @Operation(summary = "Lấy danh sách đánh giá của sản phẩm",
               description = "Trả về danh sách các đánh giá đã được phê duyệt của một sản phẩm. Không yêu cầu xác thực.")
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<List<ReviewResponse>> getProductReviews(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getReviewsByProductId(productId));
    }

    @Operation(summary = "Lấy danh sách sản phẩm khách hàng thường mua kèm",
               description = "Trả về danh sách sản phẩm mua kèm đã được tính sẵn từ các đơn hàng hoàn tất.")
    @GetMapping("/products/{productId}/frequently-bought-together")
    public ResponseEntity<List<FrequentlyBoughtTogetherResponse>> getFrequentlyBoughtTogether(
            @Parameter(description = "ID của sản phẩm gốc", example = "1", required = true)
            @PathVariable Long productId,
            @Parameter(description = "Số lượng gợi ý cần lấy, từ 1 đến 10", example = "4")
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(productRecommendationService.getFrequentlyBoughtTogether(productId, limit));
    }

    @Operation(summary = "Ghi nhận click vào sản phẩm mua kèm",
               description = "Endpoint public nhẹ để đo CTR của widget Khách hàng thường mua kèm.")
    @PostMapping("/products/{productId}/frequently-bought-together/clicks")
    public ResponseEntity<Void> trackFrequentlyBoughtTogetherClick(
            @Parameter(description = "ID của sản phẩm gốc", example = "1", required = true)
            @PathVariable Long productId,
            @Valid @RequestBody RecommendationClickRequest request) {
        productRecommendationService.trackClick(productId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lấy danh sách đánh giá của sản phẩm theo slug",
               description = "Trả về danh sách các đánh giá đã được phê duyệt của một sản phẩm dựa trên slug. Không yêu cầu xác thực.")
    @GetMapping("/products/slug/{slug}/reviews")
    public ResponseEntity<List<ReviewResponse>> getProductReviewsBySlug(
            @Parameter(description = "Slug của sản phẩm", example = "thuc-an-tom-tomboy-t12", required = true)
            @PathVariable String slug) {
        return ResponseEntity.ok(reviewService.getReviewsByProductSlug(slug));
    }

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

    @Operation(summary = "Chi tiết sản phẩm theo ID",
               description = "Trả về chi tiết sản phẩm dựa trên ID. Không yêu cầu xác thực.")
    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProductDetailById(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
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

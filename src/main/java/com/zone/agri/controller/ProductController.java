package com.zone.agri.controller;

import com.zone.agri.dto.product.ProductRequest;
import com.zone.agri.entity.*;
import com.zone.agri.repository.*;
import com.zone.agri.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product Management", description = "Quản lý danh mục sản phẩm, biến thể (SKUs) và kho hàng")
@CrossOrigin(origins = "http://localhost:3000")
public class ProductController {

    private final ProductService productService;

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final AttributeRepository attributeRepository;

    // =========================================================================
    // 1. CÁC API CRUD SẢN PHẨM CHÍNH
    // =========================================================================

    @Operation(summary = "Lấy danh sách sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping()
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(productService.getAll());
    }

    @Operation(summary = "Chi tiết sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy sản phẩm",
                    content = @Content(schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @Operation(summary = "Tạo mới sản phẩm", description = "Hỗ trợ thêm tự động thuộc tính động (Dynamic Attributes).")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.createProduct(request));
    }

    @Operation(summary = "Cập nhật sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @Operation(summary = "Xóa sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID của sản phẩm cần xóa", example = "1", required = true)
            @PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // 2. CÁC API LẤY DANH SÁCH GỢI Ý (CHO FORM UI)
    // =========================================================================

    @Operation(summary = "Lấy danh sách danh mục")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @Operation(summary = "Lấy danh sách thương hiệu")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/brands")
    public ResponseEntity<List<Brand>> getAllBrands() {
        return ResponseEntity.ok(brandRepository.findAll());
    }

    @Operation(summary = "Lấy danh sách Từ điển Thuộc tính động", description = "Lấy các thuộc tính đã được tạo (Màu sắc, Dạng bào chế, v.v.)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/attributes")
    public ResponseEntity<List<Attribute>> getAllAttributes() {
        return ResponseEntity.ok(attributeRepository.findAll());
    }
}
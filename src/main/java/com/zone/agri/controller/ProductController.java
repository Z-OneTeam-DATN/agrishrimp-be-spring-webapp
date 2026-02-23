package com.zone.agri.controller;

import com.zone.agri.dto.common.ApiResponse;
import com.zone.agri.dto.product.CreateProductRequest;
import com.zone.agri.dto.product.CreateProductResponse;
import com.zone.agri.dto.product.ProductRequest;
import com.zone.agri.entity.*;
import com.zone.agri.repository.*;
import com.zone.agri.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    // POST /api/products — Tạo sản phẩm mới (multipart/form-data)
    // =========================================================================

    @Operation(
        summary = "Tạo sản phẩm mới",
        description = """
            Tạo sản phẩm mới với danh sách biến thể (SKU) và quy đổi đơn vị.
            Nhận request dạng **multipart/form-data**:
            - `data` (part): JSON string của CreateProductRequest
            - `productImages` (part, optional): Danh sách ảnh sản phẩm (MultipartFile[])
            - `variantImages` (part, optional): Ảnh từng biến thể theo thứ tự index (MultipartFile[])
            """
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CreateProductResponse>> create(
            @RequestPart("data")
            @Valid CreateProductRequest request,

            @RequestPart(value = "productImages", required = false)
            List<MultipartFile> productImages,

            @RequestPart(value = "variantImages", required = false)
            List<MultipartFile> variantImages) {

        CreateProductResponse response = productService.createProduct(request, productImages, variantImages);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tạo sản phẩm thành công"));
    }

    // =========================================================================
    // GET /api/products
    // =========================================================================

    @Operation(summary = "Lấy danh sách sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(productService.getAll());
    }

    // =========================================================================
    // GET /api/products/{id}
    // =========================================================================

    @Operation(summary = "Chi tiết sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    // =========================================================================
    // PUT /api/products/{id} — Cập nhật (API cũ – tương thích FE hiện tại)
    // =========================================================================

    @Operation(summary = "Cập nhật sản phẩm (JSON)")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    // =========================================================================
    // DELETE /api/products/{id}
    // =========================================================================

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
    // Form-data helpers (dropdown cho UI)
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

    @Operation(summary = "Lấy danh sách Từ điển Thuộc tính động")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/attributes")
    public ResponseEntity<List<Attribute>> getAllAttributes() {
        return ResponseEntity.ok(attributeRepository.findAll());
    }
}

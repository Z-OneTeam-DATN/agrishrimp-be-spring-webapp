package com.zone.agri.controller;

import com.zone.agri.dto.response.common.ApiResponse;
import com.zone.agri.dto.response.admin.AttributeDTO;
import com.zone.agri.dto.request.product.CreateProductJsonWrapper;
import com.zone.agri.dto.request.product.CreateProductRequest;
import com.zone.agri.dto.response.product.ProductResponse;
import com.zone.agri.dto.request.product.ProductRequest;
import com.zone.agri.entity.*;
import com.zone.agri.repository.*;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.AttributeService;
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
public class ProductController {

    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final SupplierRepository supplierRepository;
    private final AttributeService attributeService;

    // =========================================================================
    // POST /api/products — Tạo sản phẩm mới (multipart/form-data)
    // =========================================================================

    @Operation(
            summary = "Tạo sản phẩm mới (JSON)",
            description = """
            Tạo sản phẩm không kèm ảnh. Gửi request dạng **application/json**:
            ```json
            { "data": { "name": "...", "categoryId": 1, "variants": [...] } }
            ```
            """
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_CREATE")
    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> createJson(
            @RequestBody @Valid CreateProductJsonWrapper wrapper) {

        ProductResponse response = productService.createProduct(wrapper.getData(), null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Tạo sản phẩm mới (multipart — có ảnh)",
            description = """
            Tạo sản phẩm kèm upload ảnh. Gửi request dạng **multipart/form-data**:
            - `data` (part, Content-Type: application/json): JSON của CreateProductRequest
            - `productImages` (part, optional): Danh sách ảnh sản phẩm chính
            - `variantImages` (part, optional): Ảnh biến thể theo đúng thứ tự index
            """
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_CREATE")
    @PostMapping(value = "/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createMultipart(
            @RequestPart("data")
            @Valid CreateProductRequest request,

            @RequestPart(value = "productImages", required = false)
            List<MultipartFile> productImages,

            @RequestPart(value = "variantImages", required = false)
            List<MultipartFile> variantImages) {

        ProductResponse response = productService.createProduct(request, productImages, variantImages);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // GET /api/products
    // =========================================================================

    @Operation(summary = "Lấy danh sách sản phẩm (có lọc)")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_VIEW")
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAll(
            @Parameter(description = "Tìm kiếm theo tên, thương hiệu hoặc mã SKU", example = "Sản phẩm")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo ID danh mục", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Lọc theo trạng thái (ACTIVE/INACTIVE/DRAFT)", example = "ACTIVE")
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(productService.getAll(keyword, categoryId, status));
    }

    // =========================================================================
    // GET /api/products/{id}
    // =========================================================================

    @Operation(summary = "Chi tiết sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_VIEW")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    // =========================================================================
    // PUT /api/products/{id} — Cập nhật (Sửa lại thành form-data để nhận ảnh)
    // =========================================================================

    @Operation(summary = "Cập nhật sản phẩm (multipart - có ảnh)")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_UPDATE")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> update(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id,

            @RequestPart("data")
            @Valid ProductRequest request,

            @RequestPart(value = "productImages", required = false)
            List<MultipartFile> productImages,

            @RequestPart(value = "variantImages", required = false)
            List<MultipartFile> variantImages) {

        return ResponseEntity.ok(productService.updateProduct(id, request, productImages, variantImages));
    }

    // =========================================================================
    // DELETE /api/products/{id}
    // =========================================================================

    @Operation(summary = "Xóa sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "ID của sản phẩm cần xóa", example = "1", required = true)
            @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa sản phẩm thành công"));
    }

    @Operation(summary = "Ngừng kinh doanh sản phẩm")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_UPDATE")
    @PutMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id) {
        productService.disableProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Ngừng kinh doanh sản phẩm thành công"));
    }

    @Operation(summary = "Kích hoạt kinh doanh lại sản phẩm", description = "Chuyển trạng thái sản phẩm từ INACTIVE sang ACTIVE")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_UPDATE")
    @PutMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<Void>> enable(
            @Parameter(description = "ID của sản phẩm", example = "1", required = true)
            @PathVariable Long id) {
        productService.enableProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Kích hoạt kinh doanh sản phẩm thành công."));
    }

    // =========================================================================
    // Form-data helpers (dropdown cho UI)
    // =========================================================================

    @Operation(summary = "Lấy danh sách danh mục")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_VIEW")
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @Operation(summary = "Lấy danh sách thương hiệu")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_VIEW")
    @GetMapping("/brands")
    public ResponseEntity<List<Brand>> getAllBrands() {
        return ResponseEntity.ok(brandRepository.findAll());
    }

    @Operation(summary = "Lấy danh sách nhà cung cấp (cho form sản phẩm)")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_VIEW")
    @GetMapping("/suppliers-list")
    public ResponseEntity<List<Supplier>> getActiveSuppliers() {
        return ResponseEntity.ok(supplierRepository.findByStatus(com.zone.agri.entity.enums.SupplierStatus.ACTIVE));
    }

    @Operation(summary = "Lấy danh sách Từ điển Thuộc tính động")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_VIEW")
    @GetMapping("/attributes")
    public ResponseEntity<List<AttributeDTO>> getAllAttributes() {
        return ResponseEntity.ok(attributeService.getAll());
    }

    // =========================================================================
    // IMAGE SEARCH ENDPOINTS
    // =========================================================================

    @Operation(
            summary = "Tìm kiếm sản phẩm bằng hình ảnh",
            description = """
            Upload một ảnh, hệ thống sẽ trả về danh sách sản phẩm tương tự được tìm từ AI service.
            Gửi request dạng **multipart/form-data** với field `image`.
            Trả 503 nếu AI service bị down.
            """
    )
    @PostMapping(value = "/search-by-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ProductResponse>> searchByImage(
            @RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(productService.searchByImage(image));
    }

    @Operation(
            summary = "Index toàn bộ sản phẩm vào AI service",
            description = """
            Gọi một lần khi deploy để đồng bộ toàn bộ sản phẩm có ảnh vào AI service.
            Yêu cầu quyền PRODUCT_UPDATE.
            """
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_UPDATE")
    @PostMapping("/index-all")
    public ResponseEntity<ApiResponse<String>> indexAll() {
        int count = productService.indexAll();
        return ResponseEntity.ok(ApiResponse.success(null, "Đã index " + count + " sản phẩm vào AI service"));
    }
}

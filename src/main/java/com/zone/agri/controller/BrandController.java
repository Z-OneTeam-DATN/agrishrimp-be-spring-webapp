package com.zone.agri.controller;

import com.zone.agri.dto.request.product.BrandRequest;
import com.zone.agri.dto.response.common.ApiResponse;
import com.zone.agri.dto.response.product.BrandResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@Tag(name = "Brand Management", description = "Quản lý danh sách thương hiệu (Admin)")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @Operation(summary = "Lấy tất cả thương hiệu (kể cả INACTIVE)", description = "Dành cho admin quản lý")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_VIEW")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAll(
            @RequestParam(required = false) String keyword) {
        List<BrandResponse> data = brandService.getAllBrands(keyword);
        ApiResponse<List<BrandResponse>> response = ApiResponse.success(data, "Lấy danh sách thương hiệu thành công");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Xem chi tiết thương hiệu", description = "Lấy thông tin thương hiệu theo ID")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_VIEW")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> getById(@PathVariable Long id) {
        BrandResponse data = brandService.getBrandById(id);
        ApiResponse<BrandResponse> response = ApiResponse.success(data, "Lấy thông tin thương hiệu thành công");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Tạo thương hiệu mới", description = "Thêm mới một thương hiệu")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_CREATE")
    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> create(
            @Valid @RequestBody BrandRequest request, Errors errors) {
        if (errors.hasErrors()) {
            String error = errors.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }
        try {
            BrandResponse data = brandService.createBrand(request);
            ApiResponse<BrandResponse> response = ApiResponse.success(data, "Tạo thương hiệu thành công");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (com.zone.agri.exception.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật thương hiệu", description = "Chỉnh sửa thương hiệu")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> update(
            @PathVariable Long id, @Valid @RequestBody BrandRequest request, Errors errors) {
        if (errors.hasErrors()) {
            String error = errors.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }
        try {
            BrandResponse data = brandService.updateBrand(id, request);
            ApiResponse<BrandResponse> response = ApiResponse.success(data, "Cập nhật thương hiệu thành công");
            return ResponseEntity.ok(response);
        } catch (com.zone.agri.exception.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (com.zone.agri.exception.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Xóa thương hiệu", description = "Xóa thương hiệu nếu chưa có sản phẩm liên kết")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("PRODUCT_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        try {
            brandService.deleteBrand(id);
            ApiResponse<Void> response = ApiResponse.success(null, "Xóa thương hiệu thành công");
            return ResponseEntity.ok(response);
        } catch (com.zone.agri.exception.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (com.zone.agri.exception.BadRequestException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}

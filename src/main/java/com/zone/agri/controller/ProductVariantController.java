package com.zone.agri.controller;

import com.zone.agri.dto.product.VariantSearchResponse;
import com.zone.agri.service.ProductVariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-variants")
@RequiredArgsConstructor
@Tag(name = "Product Variant Management", description = "Quản lý biến thể sản phẩm")
@CrossOrigin(origins = "http://localhost:3000")
public class ProductVariantController {

    private final ProductVariantService variantService;

    @Operation(summary = "Tìm kiếm biến thể sản phẩm (Dùng cho tạo đơn/chuyển kho)")
    @GetMapping("/search")
    public ResponseEntity<List<VariantSearchResponse>> searchVariants(@RequestParam String keyword) {
        return ResponseEntity.ok(variantService.searchVariants(keyword));
    }
}
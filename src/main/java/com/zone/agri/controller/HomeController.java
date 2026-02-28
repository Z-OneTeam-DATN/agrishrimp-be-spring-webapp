package com.zone.agri.controller;

import com.zone.agri.dto.product.ProductResponse;
import com.zone.agri.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/home")
@RequiredArgsConstructor
@Tag(name = "Home API", description = "API dành cho khách hàng - Trang chủ")
public class HomeController {

    private final ProductService productService;

    @Operation(summary = "Danh sách sản phẩm đang bán (có tồn kho)")
    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getProducts() {
        return ResponseEntity.ok(productService.getProductsForSale());
    }

    @Operation(summary = "Top 5-10 sản phẩm bán chạy nhất")
    @GetMapping("/best-sellers")
    public ResponseEntity<List<ProductResponse>> getBestSellers(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(productService.getTopBestSellers(limit));
    }

    @Operation(summary = "Chi tiết sản phẩm cho khách hàng qua slug")
    @GetMapping("/product/{slug}")
    public ResponseEntity<ProductResponse> getDetail(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductDetailForUser(slug));
    }
}
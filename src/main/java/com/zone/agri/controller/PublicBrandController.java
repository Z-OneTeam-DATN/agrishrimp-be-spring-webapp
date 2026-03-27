package com.zone.agri.controller;

import com.zone.agri.dto.response.product.BrandResponse;
import com.zone.agri.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/brands")
@RequiredArgsConstructor
@Tag(name = "Public Brand APIs", description = "Các API công khai để lấy thông tin thương hiệu")
@Slf4j
public class PublicBrandController {

    private final BrandService brandService;

    @Operation(summary = "Lấy danh sách thương hiệu đang hoạt động", 
               description = "Trả về danh sách thương hiệu đang hoạt động cho người dùng cuối. Không yêu cầu xác thực.")
    @GetMapping
    public ResponseEntity<List<BrandResponse>> getPublicBrands() {
        log.info("REST request to get all public brands");
        return ResponseEntity.ok(brandService.getPublicBrands());
    }
}

package com.zone.agri.controller;

import com.zone.agri.dto.request.banner.BannerRequest;
import com.zone.agri.dto.response.banner.BannerResponse;
import com.zone.agri.dto.response.common.ApiResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.BannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
@Tag(name = "Banner Management", description = "Quản lý banner trang chủ")
@SecurityRequirement(name = "bearerAuth")
public class BannerController {

    private final BannerService bannerService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @RequirePermission("BANNER_VIEW")
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getAll(), "OK"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("BANNER_CREATE")
    public ResponseEntity<ApiResponse<BannerResponse>> create(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "mobileFile", required = false) MultipartFile mobileFile) throws Exception {
        BannerRequest req = objectMapper.readValue(dataJson, BannerRequest.class);
        return ResponseEntity.ok(ApiResponse.success(bannerService.create(req, file, mobileFile), "Tạo banner thành công"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("BANNER_EDIT")
    public ResponseEntity<ApiResponse<BannerResponse>> update(
            @PathVariable Long id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "mobileFile", required = false) MultipartFile mobileFile) throws Exception {
        BannerRequest req = objectMapper.readValue(dataJson, BannerRequest.class);
        return ResponseEntity.ok(ApiResponse.success(bannerService.update(id, req, file, mobileFile), "Cập nhật banner thành công"));
    }

    @PatchMapping("/{id}/toggle")
    @RequirePermission("BANNER_EDIT")
    public ResponseEntity<ApiResponse<Void>> toggle(@PathVariable Long id) {
        bannerService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã cập nhật trạng thái"));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("BANNER_DELETE")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa banner"));
    }
}

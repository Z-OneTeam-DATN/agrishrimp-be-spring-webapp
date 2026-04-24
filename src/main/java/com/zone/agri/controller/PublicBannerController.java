package com.zone.agri.controller;

import com.zone.agri.dto.response.banner.BannerResponse;
import com.zone.agri.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/banners")
@RequiredArgsConstructor
@Tag(name = "Public Banner APIs", description = "Lấy danh sách banner đang hoạt động cho trang chủ")
public class PublicBannerController {

    private final BannerService bannerService;

    @Operation(summary = "Lấy banner đang hoạt động", description = "Không yêu cầu xác thực")
    @GetMapping
    public ResponseEntity<List<BannerResponse>> getPublicBanners() {
        return ResponseEntity.ok(bannerService.getPublicBanners());
    }
}

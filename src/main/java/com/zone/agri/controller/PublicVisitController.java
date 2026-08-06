package com.zone.agri.controller;

import com.zone.agri.dto.request.visit.TrackVisitRequest;
import com.zone.agri.service.VisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/visits")
@RequiredArgsConstructor
@Tag(name = "Public Visit Tracking", description = "Ghi nhận lượt xem trang ẩn danh cho trang tổng quan. Không yêu cầu xác thực.")
public class PublicVisitController {

    private final VisitService visitService;

    @Operation(summary = "Ghi nhận 1 lượt xem trang",
               description = "Được gọi bởi middleware Next.js cho mỗi lượt điều hướng trang ở storefront.")
    @PostMapping("/track")
    public ResponseEntity<Void> track(@Valid @RequestBody TrackVisitRequest request) {
        visitService.trackVisit(request.getVisitorId(), request.getPath(), request.getUserAgent());
        return ResponseEntity.noContent().build();
    }
}

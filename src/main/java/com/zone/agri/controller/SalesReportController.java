package com.zone.agri.controller;

import com.zone.agri.dto.response.report.SalesReportDetailResponse;
import com.zone.agri.dto.response.report.SalesReportSummaryResponse;
import com.zone.agri.service.SalesReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports/sales")
@RequiredArgsConstructor
@Tag(name = "Sales Report API", description = "API báo cáo bán hàng")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN', 'STAFF')")
public class SalesReportController {

    private final SalesReportService salesReportService;

    @Operation(summary = "Tổng quan báo cáo bán hàng")
    @GetMapping("/summary")
    public ResponseEntity<SalesReportSummaryResponse> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(salesReportService.getSummary(startDate, endDate, branchId));
    }

    @Operation(summary = "Chi tiết báo cáo bán hàng theo loại")
    @GetMapping("/detail")
    public ResponseEntity<SalesReportDetailResponse> getDetail(
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(salesReportService.getDetail(type, startDate, endDate, branchId));
    }
}

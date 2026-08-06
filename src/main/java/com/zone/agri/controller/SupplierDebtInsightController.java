package com.zone.agri.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.supplier.SupplierDebtInsightResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.SupplierDebtInsightService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/supplier-debt")
@RequiredArgsConstructor
@Slf4j
public class SupplierDebtInsightController {

    private final SupplierDebtInsightService supplierDebtInsightService;

    @GetMapping("/{branchId}/insight-analysis")
    @RequirePermission("REPORT_FINANCE_VIEW")
    public ResponseEntity<SupplierDebtInsightResponse> getSupplierDebtInsights(
            @PathVariable String branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "true") boolean compareWithPreviousPeriod) {

        log.info("Requesting Supplier Debt Insights for branchId: {}, startDate: {}, endDate: {}, compareTrend: {}", 
                branchId, startDate, endDate, compareWithPreviousPeriod);

        Long requestedBranchId = null;
        if (!"ALL".equalsIgnoreCase(branchId) && !"all".equalsIgnoreCase(branchId)) {
            try {
                requestedBranchId = Long.parseLong(branchId);
            } catch (NumberFormatException e) {
                log.warn("Invalid branch ID: {}, fallback to ALL", branchId);
            }
        }

        // Ép về đúng chi nhánh của người dùng (nếu có), giống mọi endpoint tài chính khác —
        // tránh IDOR: trước đây branchId lấy thẳng từ URL không qua kiểm tra, một tài khoản bị
        // khoá vào 1 chi nhánh có thể đổi URL để xem công nợ chi nhánh khác.
        Long finalBranchId = AuthUtils.resolveRequestedOrUserBranch(
                requestedBranchId, "REPORT_FINANCE_VIEW", "REPORT_FINANCE_VIEW_ALL_BRANCHES");

        SupplierDebtInsightResponse response = supplierDebtInsightService
                .getSupplierDebtInsights(startDate, endDate, finalBranchId, compareWithPreviousPeriod);

        return ResponseEntity.ok(response);
    }
}

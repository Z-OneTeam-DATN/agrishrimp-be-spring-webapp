package com.zone.agri.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        Long finalBranchId = null;
        if (!"ALL".equalsIgnoreCase(branchId) && !"all".equalsIgnoreCase(branchId)) {
            try {
                finalBranchId = Long.parseLong(branchId);
            } catch (NumberFormatException e) {
                log.warn("Invalid branch ID: {}, fallback to ALL", branchId);
            }
        }

        SupplierDebtInsightResponse response = supplierDebtInsightService
                .getSupplierDebtInsights(startDate, endDate, finalBranchId, compareWithPreviousPeriod);

        return ResponseEntity.ok(response);
    }
}

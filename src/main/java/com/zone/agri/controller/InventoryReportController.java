package com.zone.agri.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.inventory.InventoryIOSummaryResponse;
import com.zone.agri.dto.response.inventory.InventoryLedgerEntryResponse;
import com.zone.agri.dto.response.inventory.StockSummaryResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.InventoryReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/inventory-reports")
@RequiredArgsConstructor
@RequirePermission("REPORT_INVENTORY_VIEW")
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;

    @GetMapping("/stock-summary")
    public ResponseEntity<List<StockSummaryResponse>> getStockSummary(
            @RequestParam(required = false) Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        return ResponseEntity.ok(inventoryReportService.getStockSummary(finalBranchId));
    }

    @GetMapping("/ledger")
    public ResponseEntity<List<InventoryLedgerEntryResponse>> getLedger(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "all") String direction) {
        Long finalBranchId = resolveBranchId(branchId);
        return ResponseEntity.ok(inventoryReportService.getLedger(finalBranchId, startDate, endDate, direction));
    }

    @GetMapping("/io-summary")
    public ResponseEntity<List<InventoryIOSummaryResponse>> getIOSummary(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long finalBranchId = resolveBranchId(branchId);
        return ResponseEntity.ok(inventoryReportService.getIOSummary(finalBranchId, startDate, endDate));
    }

    private Long resolveBranchId(Long requestedBranchId) {
        return AuthUtils.resolveRequestedOrUserBranch(
                requestedBranchId, "REPORT_INVENTORY_VIEW", "REPORT_INVENTORY_VIEW_ALL_BRANCHES");
    }
}

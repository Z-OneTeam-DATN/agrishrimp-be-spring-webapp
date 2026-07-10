package com.zone.agri.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.response.financial.CashflowRiskResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.CashflowRiskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/cashbook")
@RequiredArgsConstructor
@RequirePermission("REPORT_FINANCE_VIEW")
@Slf4j
public class CashbookRiskController {

    private final CashflowRiskService cashflowRiskService;
    private final WarehouseContext warehouseContext;

    @GetMapping("/{branchIdString}/risk-analysis")
    public ResponseEntity<CashflowRiskResponse> getRiskAnalysis(
            @PathVariable String branchIdString,
            @RequestParam(required = false) Integer windowDays) {
        log.info("REST request for cashflow risk analysis: branchIdString={}, windowDays={}", branchIdString, windowDays);

        Long branchId = null;
        if (!"ALL".equalsIgnoreCase(branchIdString.trim())) {
            try {
                branchId = Long.parseLong(branchIdString.trim());
            } catch (NumberFormatException e) {
                throw new com.zone.agri.exception.BadRequestException("Mã chi nhánh không hợp lệ");
            }
        }

        // Validate branch permission mapping
        if (branchId != null) {
            warehouseContext.assertAccess(branchId);
        }

        return ResponseEntity.ok(cashflowRiskService.analyzeCashflowRisk(branchId, windowDays));
    }
}

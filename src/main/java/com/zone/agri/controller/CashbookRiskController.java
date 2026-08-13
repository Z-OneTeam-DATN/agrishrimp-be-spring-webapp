package com.zone.agri.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zone.agri.common.AuthUtils;
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

    @GetMapping("/{branchIdString}/risk-analysis")
    public ResponseEntity<CashflowRiskResponse> getRiskAnalysis(
            @PathVariable String branchIdString,
            @RequestParam(required = false) Integer windowDays) {
        log.info("REST request for cashflow risk analysis: branchIdString={}, windowDays={}", branchIdString, windowDays);

        Long requestedBranchId = null;
        if (!"ALL".equalsIgnoreCase(branchIdString.trim())) {
            try {
                requestedBranchId = Long.parseLong(branchIdString.trim());
            } catch (NumberFormatException e) {
                throw new com.zone.agri.exception.BadRequestException("Mã chi nhánh không hợp lệ");
            }
        }

        Long branchId = AuthUtils.resolveRequestedOrUserBranch(
                requestedBranchId, "REPORT_FINANCE_VIEW", "REPORT_FINANCE_VIEW_ALL_BRANCHES");

        return ResponseEntity.ok(cashflowRiskService.analyzeCashflowRisk(branchId, windowDays));
    }
}


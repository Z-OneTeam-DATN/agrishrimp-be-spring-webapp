package com.zone.agri.controller;

import com.zone.agri.dto.response.financial.ProfitLossResponse;
import com.zone.agri.dto.response.supplier.SupplierDebtResponse;
import com.zone.agri.service.FinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/financial")
@RequiredArgsConstructor
public class FinancialController {

    private final FinancialService financialService;

    @GetMapping("/profit-loss")
    public ResponseEntity<ProfitLossResponse> getProfitLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(financialService.getProfitLossReport(startDate, endDate, branchId));
    }

    @GetMapping("/supplier-debts")
    public ResponseEntity<List<SupplierDebtResponse>> getSupplierDebts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false, defaultValue = "not_zero") String debtFilter) {
        return ResponseEntity.ok(financialService.getSupplierDebts(search, startDate, endDate, branchId, staffId, debtFilter));
    }
}
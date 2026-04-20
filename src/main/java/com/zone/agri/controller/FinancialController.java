package com.zone.agri.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zone.agri.dto.response.financial.ProfitLossResponse;
import com.zone.agri.dto.response.inventory.ReceiptPaymentResponse;
import com.zone.agri.dto.response.supplier.SupplierDebtResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.FinancialService;
import com.zone.agri.service.InventoryReceiptPaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/financial")
@RequiredArgsConstructor
@RequirePermission("REPORT_FINANCE_VIEW")
public class FinancialController {

    private final FinancialService financialService;
    private final InventoryReceiptPaymentService inventoryReceiptPaymentService;

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
        return ResponseEntity
                .ok(financialService.getSupplierDebts(search, startDate, endDate, branchId, staffId, debtFilter));
    }

    @GetMapping("/supplier-payments")
    public ResponseEntity<List<ReceiptPaymentResponse>> getSupplierPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(inventoryReceiptPaymentService.getAllPayments(startDate, endDate, branchId));
    }
}

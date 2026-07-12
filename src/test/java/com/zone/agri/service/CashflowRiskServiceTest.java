package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.dto.response.financial.*;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SupplierRepository;

@ExtendWith(MockitoExtension.class)
class CashflowRiskServiceTest {

    @Mock
    private SettingService settingService;

    @Mock
    private FinancialService financialService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private InventoryNoteRepository inventoryNoteRepository;

    @InjectMocks
    private CashflowRiskService cashflowRiskService;

    @Test
    void analyzeCashflowRisk_shouldReturnSafeWhenNoDebts() {
        // Setup configuration defaults
        when(settingService.getCashflowRiskWindowDays()).thenReturn(14);
        when(settingService.getCashflowCriticalThresholdPercent()).thenReturn(BigDecimal.valueOf(20.0));
        when(settingService.getCashflowWeightTime()).thenReturn(BigDecimal.valueOf(0.5));
        when(settingService.getCashflowWeightFrequency()).thenReturn(BigDecimal.valueOf(0.3));
        when(settingService.getCashflowWeightValue()).thenReturn(BigDecimal.valueOf(0.2));
        when(settingService.getSupplierDebtDefaultTermDays()).thenReturn(30);

        // Setup mock cashbook report returning 100M VND closing balance
        CashbookSummaryResponse summary = new CashbookSummaryResponse();
        summary.setClosingBalance(new BigDecimal("100000000.00"));
        CashbookReportResponse cashbookRes = CashbookReportResponse.builder()
                .summary(summary)
                .entries(new ArrayList<>())
                .build();
        when(financialService.getCashbookReport(any(), any(), eq(1L))).thenReturn(cashbookRes);

        // Expected inflows
        when(orderRepository.sumUnpaidOrdersAmount(1L)).thenReturn(new BigDecimal("5000000.00"));

        // No supplier debts
        when(supplierRepository.findSupplierDebtLedger(any(), any(), eq(1L), any())).thenReturn(new ArrayList<>());

        CashflowRiskResponse response = cashflowRiskService.analyzeCashflowRisk(1L, null);

        assertThat(response.isInsufficientData()).isFalse();
        assertThat(response.getRiskLevel()).isEqualTo("SAFE");
        assertThat(response.getCurrentBalance()).isEqualByComparingTo("100000000.00");
        assertThat(response.getExpectedInflow()).isEqualByComparingTo("5000000.00");
        assertThat(response.getTotalDebtDueInWindow()).isZero();
        assertThat(response.getShortfallAmount()).isZero();
    }

    @Test
    void analyzeCashflowRisk_shouldReturnCriticalWhenNegativeBalanceButNoDebts() {
        // Setup configuration defaults
        when(settingService.getCashflowRiskWindowDays()).thenReturn(14);
        when(settingService.getCashflowCriticalThresholdPercent()).thenReturn(BigDecimal.valueOf(20.0));
        when(settingService.getCashflowWeightTime()).thenReturn(BigDecimal.valueOf(0.5));
        when(settingService.getCashflowWeightFrequency()).thenReturn(BigDecimal.valueOf(0.3));
        when(settingService.getCashflowWeightValue()).thenReturn(BigDecimal.valueOf(0.2));
        when(settingService.getSupplierDebtDefaultTermDays()).thenReturn(30);

        // Setup mock cashbook report returning -10M VND closing balance
        CashbookSummaryResponse summary = new CashbookSummaryResponse();
        summary.setClosingBalance(new BigDecimal("-10000000.00"));
        CashbookReportResponse cashbookRes = CashbookReportResponse.builder()
                .summary(summary)
                .entries(new ArrayList<>())
                .build();
        when(financialService.getCashbookReport(any(), any(), eq(1L))).thenReturn(cashbookRes);

        // Expected inflows: 1M
        when(orderRepository.sumUnpaidOrdersAmount(1L)).thenReturn(new BigDecimal("1000000.00"));

        // No supplier debts
        when(supplierRepository.findSupplierDebtLedger(any(), any(), eq(1L), any())).thenReturn(new ArrayList<>());

        CashflowRiskResponse response = cashflowRiskService.analyzeCashflowRisk(1L, null);

        assertThat(response.isInsufficientData()).isFalse();
        assertThat(response.getRiskLevel()).isEqualTo("CRITICAL");
        assertThat(response.getCurrentBalance()).isEqualByComparingTo("-10000000.00");
        assertThat(response.getExpectedInflow()).isEqualByComparingTo("1000000.00");
        assertThat(response.getProjectedBalance()).isEqualByComparingTo("-9000000.00");
        assertThat(response.getTotalDebtDueInWindow()).isZero();
        assertThat(response.getWarnings()).contains("Quỹ hiện đang âm 10.000.000 đồng.");
    }

    @Test
    void analyzeCashflowRisk_shouldReturnCriticalWhenShortfallExists() {
        // Setup configuration defaults
        when(settingService.getCashflowRiskWindowDays()).thenReturn(14);
        when(settingService.getCashflowCriticalThresholdPercent()).thenReturn(BigDecimal.valueOf(20.0));
        when(settingService.getCashflowWeightTime()).thenReturn(BigDecimal.valueOf(0.5));
        when(settingService.getCashflowWeightFrequency()).thenReturn(BigDecimal.valueOf(0.3));
        when(settingService.getCashflowWeightValue()).thenReturn(BigDecimal.valueOf(0.2));
        when(settingService.getSupplierDebtDefaultTermDays()).thenReturn(30);

        // Setup cashbook balance at 1M VND
        CashbookSummaryResponse summary = new CashbookSummaryResponse();
        summary.setClosingBalance(new BigDecimal("1000000.00"));
        CashbookReportResponse cashbookRes = CashbookReportResponse.builder()
                .summary(summary)
                .entries(new ArrayList<>())
                .build();
        when(financialService.getCashbookReport(any(), any(), eq(1L))).thenReturn(cashbookRes);

        // Expected inflow: 2M VND
        when(orderRepository.sumUnpaidOrdersAmount(1L)).thenReturn(new BigDecimal("2000000.00"));

        // Supplier ledger: supplier owes 5M due in 10 days (unpaid import note created 20 days ago)
        // Since term is 30 days, 20 days old import note is due in 10 days (which is inside 14 days window)
        SupplierRepository.SupplierDebtLedgerProjection proj = new SupplierRepository.SupplierDebtLedgerProjection() {
            @Override
            public Long getSupplierId() { return 10L; }
            @Override
            public String getSupplierCode() { return "SUP-10"; }
            @Override
            public String getSupplierName() { return "NCC A"; }
            @Override
            public String getPhone() { return "090123456"; }
            @Override
            public Long getNoteId() { return 101L; }
            @Override
            public InventoryNoteType getNoteType() { return InventoryNoteType.IMPORT; }
            @Override
            public BigDecimal getTotalAmount() { return new BigDecimal("5000000.00"); }
            @Override
            public BigDecimal getPaidAmount() { return BigDecimal.ZERO; }
            @Override
            public LocalDateTime getCreatedAt() { return LocalDateTime.now().minusDays(20); }
            @Override
            public Long getCreatedById() { return null; }
            @Override
            public String getCreatedByName() { return null; }
        };

        when(supplierRepository.findSupplierDebtLedger(any(), any(), eq(1L), any())).thenReturn(List.of(proj));
        when(inventoryNoteRepository.countCompletedImportsSince(eq(10L), any())).thenReturn(5L);

        CashflowRiskResponse response = cashflowRiskService.analyzeCashflowRisk(1L, null);

        assertThat(response.isInsufficientData()).isFalse();
        // currentBalance (1M) + expectedInflow (2M) = projected (3M). Debt due (5M) > projected (3M) => CRITICAL
        assertThat(response.getRiskLevel()).isEqualTo("CRITICAL");
        assertThat(response.getShortfallAmount()).isEqualByComparingTo("2000000.00");
        assertThat(response.getPrioritizedDebts()).hasSize(1);
        assertThat(response.getPrioritizedDebts().get(0).getSupplierName()).isEqualTo("NCC A");
        assertThat(response.getPrioritizedDebts().get(0).getPriorityRank()).isEqualTo(1);
    }

    @Test
    void analyzeCashflowRisk_shouldReturnInsufficientData() {
        // Setup configuration defaults
        when(settingService.getCashflowRiskWindowDays()).thenReturn(14);
        when(settingService.getCashflowCriticalThresholdPercent()).thenReturn(BigDecimal.valueOf(20.0));
        when(settingService.getCashflowWeightTime()).thenReturn(BigDecimal.valueOf(0.5));
        when(settingService.getCashflowWeightFrequency()).thenReturn(BigDecimal.valueOf(0.3));
        when(settingService.getCashflowWeightValue()).thenReturn(BigDecimal.valueOf(0.2));
        when(settingService.getSupplierDebtDefaultTermDays()).thenReturn(30);

        // Setup mock to return null summary
        when(financialService.getCashbookReport(any(), any(), eq(1L))).thenReturn(null);

        CashflowRiskResponse response = cashflowRiskService.analyzeCashflowRisk(1L, null);

        assertThat(response.isInsufficientData()).isTrue();
        assertThat(response.getWarnings()).contains("Không thể lấy số liệu số dư Sổ quỹ hiện tại.");
    }

    @Test
    void analyzeCashflowRisk_shouldPrioritizeMultipleSuppliersCorrectly() {
        // Setup configuration defaults
        when(settingService.getCashflowRiskWindowDays()).thenReturn(14);
        when(settingService.getCashflowCriticalThresholdPercent()).thenReturn(BigDecimal.valueOf(20.0));
        when(settingService.getCashflowWeightTime()).thenReturn(BigDecimal.valueOf(0.5));
        when(settingService.getCashflowWeightFrequency()).thenReturn(BigDecimal.valueOf(0.3));
        when(settingService.getCashflowWeightValue()).thenReturn(BigDecimal.valueOf(0.2));
        when(settingService.getSupplierDebtDefaultTermDays()).thenReturn(30);

        // Closing balance: 10M VND
        CashbookSummaryResponse summary = new CashbookSummaryResponse();
        summary.setClosingBalance(new BigDecimal("10000000.00"));
        CashbookReportResponse cashbookRes = CashbookReportResponse.builder()
                .summary(summary)
                .entries(new ArrayList<>())
                .build();
        when(financialService.getCashbookReport(any(), any(), eq(1L))).thenReturn(cashbookRes);

        // Expected inflow: 2M VND
        when(orderRepository.sumUnpaidOrdersAmount(1L)).thenReturn(new BigDecimal("2000000.00"));

        // Setup 2 supplier projections within 14-day window:
        // Supplier 10 (NCC A): outstanding 5M, created 40 days ago (overdue by 10 days)
        SupplierRepository.SupplierDebtLedgerProjection projA = new SupplierRepository.SupplierDebtLedgerProjection() {
            @Override public Long getSupplierId() { return 10L; }
            @Override public String getSupplierCode() { return "SUP-10"; }
            @Override public String getSupplierName() { return "NCC A"; }
            @Override public String getPhone() { return "090123456"; }
            @Override public Long getNoteId() { return 101L; }
            @Override public InventoryNoteType getNoteType() { return InventoryNoteType.IMPORT; }
            @Override public BigDecimal getTotalAmount() { return new BigDecimal("5000000.00"); }
            @Override public BigDecimal getPaidAmount() { return BigDecimal.ZERO; }
            @Override public LocalDateTime getCreatedAt() { return LocalDateTime.now().minusDays(40); }
            @Override public Long getCreatedById() { return null; }
            @Override public String getCreatedByName() { return null; }
        };

        // Supplier 30 (NCC C): outstanding 10M, created 25 days ago (due in 5 days)
        SupplierRepository.SupplierDebtLedgerProjection projC = new SupplierRepository.SupplierDebtLedgerProjection() {
            @Override public Long getSupplierId() { return 30L; }
            @Override public String getSupplierCode() { return "SUP-30"; }
            @Override public String getSupplierName() { return "NCC C"; }
            @Override public String getPhone() { return "090987654"; }
            @Override public Long getNoteId() { return 102L; }
            @Override public InventoryNoteType getNoteType() { return InventoryNoteType.IMPORT; }
            @Override public BigDecimal getTotalAmount() { return new BigDecimal("10000000.00"); }
            @Override public BigDecimal getPaidAmount() { return BigDecimal.ZERO; }
            @Override public LocalDateTime getCreatedAt() { return LocalDateTime.now().minusDays(25); }
            @Override public Long getCreatedById() { return null; }
            @Override public String getCreatedByName() { return null; }
        };

        when(supplierRepository.findSupplierDebtLedger(any(), any(), eq(1L), any()))
                .thenReturn(List.of(projA, projC));

        // Mock counts in last 90 days
        when(inventoryNoteRepository.countCompletedImportsSince(eq(10L), any())).thenReturn(8L);
        when(inventoryNoteRepository.countCompletedImportsSince(eq(30L), any())).thenReturn(2L);

        CashflowRiskResponse response = cashflowRiskService.analyzeCashflowRisk(1L, null);

        assertThat(response.isInsufficientData()).isFalse();
        assertThat(response.getPrioritizedDebts()).hasSize(2);

        PrioritizedDebtDto first = response.getPrioritizedDebts().get(0);
        PrioritizedDebtDto second = response.getPrioritizedDebts().get(1);

        assertThat(first.getPriorityRank()).isEqualTo(1);
        assertThat(second.getPriorityRank()).isEqualTo(2);
        assertThat(first.getPriorityScore()).isGreaterThanOrEqualTo(second.getPriorityScore());
    }
}

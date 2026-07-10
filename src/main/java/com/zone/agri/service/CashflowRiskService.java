package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zone.agri.dto.response.financial.*;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashflowRiskService {

    private final SettingService settingService;
    private final FinancialService financialService;
    private final OrderRepository orderRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryNoteRepository inventoryNoteRepository;

    private final Map<String, CashflowRiskResponse> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public void clearCache() {
        cache.clear();
        log.info("Evicted Cashflow risk analysis cache");
    }

    public CashflowRiskResponse analyzeCashflowRisk(Long branchId, Integer customWindowDays) {
        int windowDays = customWindowDays != null ? customWindowDays : settingService.getCashflowRiskWindowDays();
        String cacheKey = String.format("%s-%d-%s", branchId == null ? "ALL" : branchId.toString(), windowDays, LocalDate.now());

        if (cache.containsKey(cacheKey)) {
            log.info("Returning cached Cashflow risk analysis for key: {}", cacheKey);
            return cache.get(cacheKey);
        }

        log.info("Analyzing cashflow risk for branchId: {}, customWindowDays: {}", branchId, customWindowDays);

        // 1. Load configuration parameters
        BigDecimal criticalThresholdPercent = settingService.getCashflowCriticalThresholdPercent();
        double weightTime = settingService.getCashflowWeightTime().doubleValue();
        double weightFrequency = settingService.getCashflowWeightFrequency().doubleValue();
        double weightValue = settingService.getCashflowWeightValue().doubleValue();
        int defaultTermDays = settingService.getSupplierDebtDefaultTermDays();

        // 2. Calculate currentBalance using the exact official Cashbook summary closingBalance logic
        BigDecimal currentBalance = BigDecimal.ZERO;
        boolean insufficientData = false;
        try {
            LocalDate start = LocalDate.of(2000, 1, 1);
            LocalDate end = LocalDate.now();
            CashbookReportResponse cashbook = financialService.getCashbookReport(start, end, branchId);
            if (cashbook != null && cashbook.getSummary() != null) {
                currentBalance = cashbook.getSummary().getClosingBalance();
            } else {
                insufficientData = true;
            }
        } catch (Exception e) {
            log.error("Error retrieving current cashbook balance", e);
            insufficientData = true;
        }

        if (insufficientData) {
            return CashflowRiskResponse.builder()
                    .insufficientData(true)
                    .warnings(List.of("Không thể lấy số liệu số dư Sổ quỹ hiện tại."))
                    .build();
        }

        // 3. Calculate expectedInflow (unpaid order amounts, including completed and received COD orders)
        BigDecimal expectedInflow = BigDecimal.ZERO;
        try {
            expectedInflow = orderRepository.sumUnpaidOrdersAmount(branchId);
        } catch (Exception e) {
            log.error("Error retrieving expected inflow", e);
        }

        // 4. Fetch supplier debt ledger to find due/overdue invoices
        List<SupplierRepository.SupplierDebtLedgerProjection> ledger = new ArrayList<>();
        try {
            ledger = supplierRepository.findSupplierDebtLedger(null, LocalDateTime.now(), branchId, null);
        } catch (Exception e) {
            log.error("Error retrieving supplier debt ledger", e);
        }

        LocalDate now = LocalDate.now();
        LocalDate windowEnd = now.plusDays(windowDays);

        // Temporary helper structure for aggregating debts by supplier
        class TempDebtDetails {
            final List<LocalDate> dueDates = new ArrayList<>();
            BigDecimal outstandingAmount = BigDecimal.ZERO;
            long countOfImportsInLast90Days = 0;
        }

        Map<Long, TempDebtDetails> supplierDebtMap = new HashMap<>();
        Map<Long, String> supplierNameMap = new HashMap<>();
        BigDecimal totalDebtDueInWindow = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();
        int overdueSupplierCount = 0;

        for (SupplierRepository.SupplierDebtLedgerProjection row : ledger) {
            // Only care about completed IMPORT notes that have outstanding debt
            if (row.getNoteType() == InventoryNoteType.IMPORT && row.getCreatedAt() != null) {
                BigDecimal totalAmt = row.getTotalAmount() != null ? row.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal paidAmt = row.getPaidAmount() != null ? row.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal outstanding = totalAmt.subtract(paidAmt);

                if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                    LocalDate dueDate = row.getCreatedAt().plusDays(defaultTermDays).toLocalDate();

                    // Include if due within the window or already past due
                    if (dueDate.isBefore(windowEnd) || dueDate.isEqual(windowEnd)) {
                        Long supId = row.getSupplierId();
                        supplierNameMap.put(supId, row.getSupplierName());

                        TempDebtDetails details = supplierDebtMap.computeIfAbsent(supId, k -> new TempDebtDetails());
                        details.outstandingAmount = details.outstandingAmount.add(outstanding);
                        details.dueDates.add(dueDate);

                        totalDebtDueInWindow = totalDebtDueInWindow.add(outstanding);

                        if (dueDate.isBefore(now)) {
                            overdueSupplierCount++;
                        }
                    }
                }
            }
        }

        if (overdueSupplierCount > 0) {
            warnings.add(String.format("Có %d khoản công nợ của nhà cung cấp đã quá hạn thanh toán.", overdueSupplierCount));
        }

        // Calculate transaction frequency in the last 90 days for each supplier
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        for (Long supId : supplierDebtMap.keySet()) {
            TempDebtDetails details = supplierDebtMap.get(supId);
            try {
                details.countOfImportsInLast90Days = inventoryNoteRepository.countCompletedImportsSince(supId, ninetyDaysAgo);
            } catch (Exception e) {
                log.error("Error counting imports for supplier id " + supId, e);
            }
        }

        // 5. Rank and prioritize supplier debts
        List<PrioritizedDebtDto> prioritizedDebts = new ArrayList<>();
        for (Long supId : supplierDebtMap.keySet()) {
            TempDebtDetails details = supplierDebtMap.get(supId);
            LocalDate earliestDueDate = details.dueDates.stream().min(LocalDate::compareTo).orElse(now);

            // Time urgency component
            double timeScore;
            if (earliestDueDate.isBefore(now)) {
                long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(earliestDueDate, now);
                // Conscious design limitation: any invoice overdue by 25+ days gets the maximum timeScore of 100
                timeScore = 50.0 + Math.min(50.0, daysOverdue * 2.0);
            } else {
                long daysToDue = java.time.temporal.ChronoUnit.DAYS.between(now, earliestDueDate);
                timeScore = Math.max(0.0, 50.0 - (daysToDue * (50.0 / windowDays)));
            }

            // Frequency of relationship component (Max score 100)
            double frequencyScore = Math.min(10.0, (double) details.countOfImportsInLast90Days) * 10.0;

            // Invoice amount value component (Max score 100)
            double valueScore = 0.0;
            if (totalDebtDueInWindow.compareTo(BigDecimal.ZERO) > 0) {
                valueScore = details.outstandingAmount.divide(totalDebtDueInWindow, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100.0)).doubleValue();
            }

            double priorityScore = (timeScore * weightTime) + (frequencyScore * weightFrequency) + (valueScore * weightValue);

            prioritizedDebts.add(PrioritizedDebtDto.builder()
                    .supplierId(supId)
                    .supplierName(supplierNameMap.get(supId))
                    .outstandingAmount(details.outstandingAmount)
                    .dueDate(earliestDueDate)
                    .priorityScore(Math.round(priorityScore * 100.0) / 100.0)
                    .build());
        }

        // Sort descending by priorityScore
        prioritizedDebts.sort((a, b) -> Double.compare(b.getPriorityScore(), a.getPriorityScore()));

        // Assign ranks
        for (int i = 0; i < prioritizedDebts.size(); i++) {
            prioritizedDebts.get(i).setPriorityRank(i + 1);
        }

        // 6. Calculate projected balance and risk level
        BigDecimal projectedBalance = currentBalance.add(expectedInflow);
        BigDecimal shortfallAmount = totalDebtDueInWindow.subtract(projectedBalance).max(BigDecimal.ZERO);

        String riskLevel = "SAFE";
        if (currentBalance.compareTo(BigDecimal.ZERO) < 0 || projectedBalance.compareTo(BigDecimal.ZERO) < 0) {
            riskLevel = "CRITICAL";
        } else if (shortfallAmount.compareTo(BigDecimal.ZERO) > 0) {
            riskLevel = "CRITICAL";
        } else if (totalDebtDueInWindow.compareTo(BigDecimal.ZERO) > 0) {
            // Calculate ratio to determine WARNING level
            BigDecimal ratio = projectedBalance.multiply(BigDecimal.valueOf(100))
                    .divide(totalDebtDueInWindow, 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(criticalThresholdPercent) < 0) {
                riskLevel = "WARNING";
            }
        } else {
            riskLevel = "SAFE";
        }

        // Add warnings for negative cash balances
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,###");
        df.setDecimalFormatSymbols(new java.text.DecimalFormatSymbols(new Locale("vi", "VN")));

        if (currentBalance.compareTo(BigDecimal.ZERO) < 0) {
            warnings.add(String.format("Quỹ hiện đang âm %s đồng.", df.format(currentBalance.abs())));
        }
        if (shortfallAmount.compareTo(BigDecimal.ZERO) > 0 && totalDebtDueInWindow.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add(String.format("Dự kiến thiếu %s đồng để trả nợ NCC trong %d ngày tới.", df.format(shortfallAmount), windowDays));
        }

        // 7. Create breakdown items
        List<BreakdownItemDto> breakdown = new ArrayList<>();
        breakdown.add(new BreakdownItemDto("OPENING_BALANCE", currentBalance, "Số dư Sổ quỹ hiện tại"));
        breakdown.add(new BreakdownItemDto("EXPECTED_INFLOW", expectedInflow, "Dòng tiền thu dự kiến"));
        breakdown.add(new BreakdownItemDto("DEBT_DUE_IN_WINDOW", totalDebtDueInWindow, String.format("Tổng nợ đến hạn trong %d ngày tới", windowDays)));

        CashflowRiskResponse response = CashflowRiskResponse.builder()
                .riskLevel(riskLevel)
                .currentBalance(currentBalance)
                .expectedInflow(expectedInflow)
                .totalDebtDueInWindow(totalDebtDueInWindow)
                .projectedBalance(projectedBalance)
                .shortfallAmount(shortfallAmount)
                .prioritizedDebts(prioritizedDebts)
                .breakdown(breakdown)
                .warnings(warnings)
                .insufficientData(false)
                .build();

        cache.put(cacheKey, response);
        return response;
    }
}

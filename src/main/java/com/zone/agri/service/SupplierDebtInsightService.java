package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.dto.response.supplier.SupplierDebtInsightResponse;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.repository.SupplierRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierDebtInsightService {

    private final SupplierRepository supplierRepository;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    public SupplierDebtInsightResponse getSupplierDebtInsights(
            LocalDate startDate,
            LocalDate endDate,
            Long branchId,
            boolean compareWithPreviousPeriod) {

        List<String> warnings = new ArrayList<>();

        try {
            LocalDateTime snapshotDate = endDate != null
                    ? endDate.atTime(23, 59, 59)
                    : LocalDateTime.now();

            LocalDate start = startDate != null
                    ? startDate
                    : snapshotDate.toLocalDate().withDayOfMonth(1);

            // 1. Lấy dữ liệu công nợ chi tiết đến mốc endDate
            List<SupplierRepository.SupplierDebtLedgerProjection> ledger = supplierRepository
                    .findSupplierDebtLedger(null, snapshotDate, branchId, null);

            if (ledger == null || ledger.isEmpty()) {
                return SupplierDebtInsightResponse.builder()
                        .insufficientData(false)
                        .totalOutstandingDebt(BigDecimal.ZERO)
                        .supplierRanking(new ArrayList<>())
                        .staffDebtSummary(new ArrayList<>())
                        .breakdown(new ArrayList<>())
                        .warnings(warnings)
                        .build();
            }

            // 2. Đọc cấu hình từ DB
            int warningDays = settingService.getDebtAgeWarningDays();
            int criticalDays = settingService.getDebtAgeCriticalDays();
            BigDecimal weightAge = settingService.getDebtWeightAge();
            BigDecimal weightValue = settingService.getDebtWeightValue();

            // 3. Tính toán công nợ từng nhà cung cấp (khớp 100% logic với FinancialService)
            Map<Long, List<SupplierRepository.SupplierDebtLedgerProjection>> rowsBySupplier = ledger.stream()
                    .filter(row -> row.getSupplierId() != null)
                    .collect(Collectors.groupingBy(SupplierRepository.SupplierDebtLedgerProjection::getSupplierId));

            Map<Long, BigDecimal> debtBySupplier = new HashMap<>();
            for (Map.Entry<Long, List<SupplierRepository.SupplierDebtLedgerProjection>> entry : rowsBySupplier.entrySet()) {
                BigDecimal totalDebt = entry.getValue().stream()
                        .map(this::mapOutstandingContribution)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                debtBySupplier.put(entry.getKey(), maxZero(totalDebt));
            }

            // Lọc ra các nhà cung cấp có nợ thực tế > 0
            List<Long> activeSupplierIds = debtBySupplier.entrySet().stream()
                    .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                    .map(Map.Entry::getKey)
                    .toList();

            if (activeSupplierIds.isEmpty()) {
                return SupplierDebtInsightResponse.builder()
                        .insufficientData(false)
                        .totalOutstandingDebt(BigDecimal.ZERO)
                        .supplierRanking(new ArrayList<>())
                        .staffDebtSummary(new ArrayList<>())
                        .breakdown(new ArrayList<>())
                        .warnings(warnings)
                        .build();
            }

            BigDecimal totalOutstanding = debtBySupplier.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 4. Tính tuổi nợ trung bình theo trọng số giá trị của mỗi NCC (chỉ tính trên phiếu IMPORT còn nợ)
            Map<Long, Double> weightedAgeMap = new HashMap<>();
            for (Long supId : activeSupplierIds) {
                List<SupplierRepository.SupplierDebtLedgerProjection> supRows = rowsBySupplier.get(supId);
                
                BigDecimal totalRemainingImportDebt = BigDecimal.ZERO;
                BigDecimal sumAgeWeightedValue = BigDecimal.ZERO;

                for (SupplierRepository.SupplierDebtLedgerProjection row : supRows) {
                    if (row.getNoteType() == InventoryNoteType.IMPORT) {
                        BigDecimal totalAmount = getSafeBigDecimal(row.getTotalAmount());
                        BigDecimal paidAmount = getSafeBigDecimal(row.getPaidAmount());
                        BigDecimal remaining = totalAmount.subtract(paidAmount);
                        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                            long ageDays = 0;
                            if (row.getCreatedAt() != null) {
                                ageDays = ChronoUnit.DAYS.between(row.getCreatedAt().toLocalDate(), snapshotDate.toLocalDate());
                                if (ageDays < 0) ageDays = 0;
                            }
                            totalRemainingImportDebt = totalRemainingImportDebt.add(remaining);
                            sumAgeWeightedValue = sumAgeWeightedValue.add(remaining.multiply(BigDecimal.valueOf(ageDays)));
                        }
                    }
                }

                double weightedAge = 0.0;
                if (totalRemainingImportDebt.compareTo(BigDecimal.ZERO) > 0) {
                    weightedAge = sumAgeWeightedValue.divide(totalRemainingImportDebt, 2, RoundingMode.HALF_UP).doubleValue();
                }
                weightedAgeMap.put(supId, weightedAge);
            }

            // 5. Tính điểm ưu tiên (priorityScore) và xếp hạng (priorityRank)
            BigDecimal maxTotalDebt = debtBySupplier.entrySet().stream()
                    .filter(e -> activeSupplierIds.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ONE);
            if (maxTotalDebt.compareTo(BigDecimal.ZERO) == 0) {
                maxTotalDebt = BigDecimal.ONE;
            }

            List<SupplierDebtInsightResponse.SupplierRankingItem> rankingItems = new ArrayList<>();
            for (Long supId : activeSupplierIds) {
                SupplierRepository.SupplierDebtLedgerProjection firstRow = rowsBySupplier.get(supId).get(0);
                BigDecimal totalDebt = debtBySupplier.get(supId);
                double weightedAge = weightedAgeMap.getOrDefault(supId, 0.0);

                String ageStatus = "NORMAL";
                if (weightedAge >= criticalDays) {
                    ageStatus = "CRITICAL";
                } else if (weightedAge >= warningDays) {
                    ageStatus = "WARNING";
                }

                double normalizedAge = Math.min(1.0, weightedAge / criticalDays);
                double normalizedValue = totalDebt.divide(maxTotalDebt, 4, RoundingMode.HALF_UP).doubleValue();

                double priorityScore = (weightAge.doubleValue() * normalizedAge + weightValue.doubleValue() * normalizedValue) * 100;

                rankingItems.add(SupplierDebtInsightResponse.SupplierRankingItem.builder()
                        .supplierId(supId)
                        .supplierCode(firstRow.getSupplierCode())
                        .supplierName(firstRow.getSupplierName())
                        .phone(firstRow.getPhone())
                        .totalDebt(totalDebt)
                        .weightedAvgDebtAge(weightedAge)
                        .ageStatus(ageStatus)
                        .priorityScore(priorityScore)
                        .build());
            }

            // Sắp xếp giảm dần theo priorityScore, nếu bằng nhau thì theo totalDebt giảm dần
            rankingItems.sort((a, b) -> {
                int cmp = Double.compare(b.getPriorityScore(), a.getPriorityScore());
                if (cmp != 0) return cmp;
                return b.getTotalDebt().compareTo(a.getTotalDebt());
            });

            // Gán priorityRank
            for (int i = 0; i < rankingItems.size(); i++) {
                rankingItems.get(i).setPriorityRank(i + 1);
            }

            // 6. Thống kê theo nhân viên phụ trách mua hàng (staffDebtSummary)
            Map<Long, StaffDebtContribution> staffDebtMap = new HashMap<>();
            for (Long supId : activeSupplierIds) {
                List<SupplierRepository.SupplierDebtLedgerProjection> supRows = rowsBySupplier.get(supId);
                for (SupplierRepository.SupplierDebtLedgerProjection row : supRows) {
                    if (row.getNoteType() == InventoryNoteType.IMPORT) {
                        BigDecimal totalAmount = getSafeBigDecimal(row.getTotalAmount());
                        BigDecimal paidAmount = getSafeBigDecimal(row.getPaidAmount());
                        BigDecimal remaining = totalAmount.subtract(paidAmount);
                        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                            Long staffId = row.getCreatedById();
                            String staffName = row.getCreatedByName();
                            if (staffId != null) {
                                StaffDebtContribution contrib = staffDebtMap.computeIfAbsent(staffId, 
                                    k -> new StaffDebtContribution(staffId, staffName, BigDecimal.ZERO));
                                contrib.setTotalDebt(contrib.getTotalDebt().add(remaining));
                            }
                        }
                    }
                }
            }

            List<SupplierDebtInsightResponse.StaffDebtSummaryItem> staffSummaries = staffDebtMap.values().stream()
                    .map(c -> SupplierDebtInsightResponse.StaffDebtSummaryItem.builder()
                            .staffId(c.getStaffId())
                            .staffName(c.getStaffName() != null ? c.getStaffName() : "Nhân viên " + c.getStaffId())
                            .totalDebtFromOrders(c.getTotalDebt())
                            .build())
                    .sorted((a, b) -> b.getTotalDebtFromOrders().compareTo(a.getTotalDebtFromOrders()))
                    .toList();

            // 7. Lấy dữ liệu kỳ trước để tính toán tỷ lệ thay đổi (compareWithPreviousPeriod)
            BigDecimal totalDebtChange = null;
            if (compareWithPreviousPeriod) {
                LocalDate previousEndDate = start.minusDays(1);
                LocalDateTime prevSnapshotDate = previousEndDate.atTime(23, 59, 59);

                try {
                    List<SupplierRepository.SupplierDebtLedgerProjection> prevLedger = supplierRepository
                            .findSupplierDebtLedger(null, prevSnapshotDate, branchId, null);

                    BigDecimal prevTotalOutstanding = BigDecimal.ZERO;
                    if (prevLedger != null && !prevLedger.isEmpty()) {
                        Map<Long, List<SupplierRepository.SupplierDebtLedgerProjection>> prevRowsBySupplier = prevLedger.stream()
                                .filter(row -> row.getSupplierId() != null)
                                .collect(Collectors.groupingBy(SupplierRepository.SupplierDebtLedgerProjection::getSupplierId));

                        for (Map.Entry<Long, List<SupplierRepository.SupplierDebtLedgerProjection>> entry : prevRowsBySupplier.entrySet()) {
                            BigDecimal prevDebt = entry.getValue().stream()
                                    .map(this::mapOutstandingContribution)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            prevTotalOutstanding = prevTotalOutstanding.add(maxZero(prevDebt));
                        }
                    }

                    if (prevTotalOutstanding.compareTo(BigDecimal.ZERO) == 0) {
                        if (totalOutstanding.compareTo(BigDecimal.ZERO) > 0) {
                            warnings.add("Không thể tính toán tỷ lệ thay đổi do kỳ trước không phát sinh dư nợ.");
                        } else {
                            totalDebtChange = BigDecimal.ZERO;
                        }
                    } else {
                        BigDecimal diff = totalOutstanding.subtract(prevTotalOutstanding);
                        totalDebtChange = diff.multiply(BigDecimal.valueOf(100))
                                .divide(prevTotalOutstanding, 2, RoundingMode.HALF_UP);
                    }
                } catch (Exception ex) {
                    log.error("Lỗi khi truy vấn công nợ kỳ trước: {}", ex.getMessage());
                    warnings.add("Lỗi hệ thống khi lấy dữ liệu công nợ kỳ trước.");
                }
            }

            // 8. Tạo breakdown nhân tố
            List<SupplierDebtInsightResponse.BreakdownItem> breakdownItems = new ArrayList<>();
            breakdownItems.add(SupplierDebtInsightResponse.BreakdownItem.builder()
                    .factor("TOTAL_DEBT")
                    .value(totalOutstanding)
                    .note("Tổng số dư công nợ toàn hệ thống tại thời điểm chốt báo cáo.")
                    .build());

            long warningCount = rankingItems.stream().filter(item -> "WARNING".equals(item.getAgeStatus())).count();
            long criticalCount = rankingItems.stream().filter(item -> "CRITICAL".equals(item.getAgeStatus())).count();
            breakdownItems.add(SupplierDebtInsightResponse.BreakdownItem.builder()
                    .factor("AGE_DISTRIBUTION")
                    .value(BigDecimal.valueOf(criticalCount))
                    .note(String.format("Có %d NCC ở mức nghiêm trọng (CRITICAL) và %d NCC ở mức cảnh báo (WARNING).", 
                            criticalCount, warningCount))
                    .build());

            if (totalDebtChange != null) {
                breakdownItems.add(SupplierDebtInsightResponse.BreakdownItem.builder()
                        .factor("TREND")
                        .value(totalDebtChange)
                        .note("Tỷ lệ tăng/giảm công nợ tổng thể so với ngày chốt kỳ trước.")
                        .build());
            }

            return SupplierDebtInsightResponse.builder()
                    .insufficientData(false)
                    .totalOutstandingDebt(totalOutstanding)
                    .totalDebtChangeVsPreviousPeriod(totalDebtChange)
                    .supplierRanking(rankingItems)
                    .staffDebtSummary(staffSummaries)
                    .breakdown(breakdownItems)
                    .warnings(warnings)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi nghiêm trọng khi phân tích công nợ nhà cung cấp: {}", e.getMessage(), e);
            return SupplierDebtInsightResponse.builder()
                    .insufficientData(true)
                    .warnings(List.of("Không thể lấy dữ liệu công nợ do lỗi kết nối cơ sở dữ liệu."))
                    .build();
        }
    }

    private BigDecimal getSafeBigDecimal(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }

    private BigDecimal maxZero(BigDecimal val) {
        return val.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : val;
    }

    private BigDecimal mapOutstandingContribution(SupplierRepository.SupplierDebtLedgerProjection row) {
        BigDecimal totalAmount = getSafeBigDecimal(row.getTotalAmount());
        BigDecimal paidAmount = getSafeBigDecimal(row.getPaidAmount());
        InventoryNoteType noteType = row.getNoteType();

        if (noteType == InventoryNoteType.IMPORT) {
            BigDecimal outstanding = totalAmount.subtract(paidAmount);
            return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
        }
        if (noteType == InventoryNoteType.EXPORT) {
            return totalAmount.negate();
        }
        return BigDecimal.ZERO;
    }

    @Data
    @AllArgsConstructor
    private static class StaffDebtContribution {
        private Long staffId;
        private String staffName;
        private BigDecimal totalDebt;
    }
}

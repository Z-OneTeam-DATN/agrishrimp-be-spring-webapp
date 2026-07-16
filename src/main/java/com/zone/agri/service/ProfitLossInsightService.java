package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.zone.agri.dto.response.financial.ProfitLossContributionItemResponse;
import com.zone.agri.dto.response.financial.ProfitLossInsightResponse;
import com.zone.agri.dto.response.financial.ProfitLossResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfitLossInsightService {

    private static final String STATUS_SAFE = "SAFE";
    private static final String STATUS_WARNING = "WARNING";
    private static final String NO_PREVIOUS_DATA = "NO_PREVIOUS_DATA";

    private final FinancialService financialService;
    private final SettingService settingService;

    public ProfitLossInsightResponse getProfitLossInsights(LocalDate startDate, LocalDate endDate, Long branchId) {
        LocalDate resolvedEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate resolvedStart = startDate != null ? startDate : resolvedEnd.withDayOfMonth(1);

        if (resolvedStart.isAfter(resolvedEnd)) {
            LocalDate swap = resolvedStart;
            resolvedStart = resolvedEnd;
            resolvedEnd = swap;
        }

        ProfitLossResponse current = financialService.getProfitLossReport(resolvedStart, resolvedEnd, branchId);
        ProfitLossResponse previous = loadPreviousPeriodReport(resolvedStart, resolvedEnd, branchId);

        BigDecimal cogsRatio = calculateRatioPercent(current.getCogs(), current.getNetRevenue());
        BigDecimal returnRatio = calculateRatioPercent(current.getReturnedGoods(), current.getGrossRevenue());

        String cogsRatioStatus = shouldWarnOnCogsRatio(current, cogsRatio) ? STATUS_WARNING : STATUS_SAFE;
        String returnRatioStatus = shouldWarnOnReturnRatio(current, returnRatio) ? STATUS_WARNING : STATUS_SAFE;

        List<String> warnings = buildWarnings(current, previous, cogsRatio, cogsRatioStatus, returnRatio, returnRatioStatus);

        return ProfitLossInsightResponse.builder()
                .cogsRatio(cogsRatio)
                .cogsRatioStatus(cogsRatioStatus)
                .returnRatio(returnRatio)
                .returnRatioStatus(returnRatioStatus)
                .netProfitChangePercent(formatNetProfitChangePercent(current.getNetProfit(), previous.getNetProfit(), previous))
                .contributionBreakdown(buildContributionBreakdown(current, previous))
                .warnings(warnings)
                .build();
    }

    private ProfitLossResponse loadPreviousPeriodReport(LocalDate startDate, LocalDate endDate, Long branchId) {
        long dayDiff = ChronoUnit.DAYS.between(startDate, endDate);
        LocalDate previousEnd = startDate.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(dayDiff);
        return financialService.getProfitLossReport(previousStart, previousEnd, branchId);
    }

    private List<ProfitLossContributionItemResponse> buildContributionBreakdown(
            ProfitLossResponse current,
            ProfitLossResponse previous) {
        List<ProfitLossContributionItemResponse> items = new ArrayList<>();
        items.add(buildItem(
                "REVENUE",
                safe(current.getGrossRevenue()),
                safe(previous.getGrossRevenue()),
                "Doanh thu goc tien hang duoc ghi nhan trong ky."));
        items.add(buildItem(
                "COGS",
                safe(current.getCogs()),
                safe(previous.getCogs()),
                "Gia von hang ban da ghi nhan theo cac don da phat sinh doanh thu."));
        items.add(buildItem(
                "SHIPPING",
                netShippingImpact(current),
                netShippingImpact(previous),
                "Tac dong rong tu phi ship thu khach tru phi ship hoan tra."));
        items.add(buildItem(
                "DISCOUNT",
                netDiscountImpact(current),
                netDiscountImpact(previous),
                "Tac dong rong tu chiet khau sau khi cong lai phan duoc hoan."));
        items.add(buildItem(
                "RETURNS",
                safe(current.getReturnedGoods()),
                safe(previous.getReturnedGoods()),
                "Gia tri hang ban bi tra lai lam giam hieu qua loi nhuan."));
        return items;
    }

    private ProfitLossContributionItemResponse buildItem(
            String factor,
            BigDecimal currentValue,
            BigDecimal previousValue,
            String note) {
        return ProfitLossContributionItemResponse.builder()
                .factor(factor)
                .currentValue(currentValue)
                .previousValue(previousValue)
                .changeAmount(currentValue.subtract(previousValue))
                .note(note)
                .build();
    }

    private List<String> buildWarnings(
            ProfitLossResponse current,
            ProfitLossResponse previous,
            BigDecimal cogsRatio,
            String cogsRatioStatus,
            BigDecimal returnRatio,
            String returnRatioStatus) {
        List<String> warnings = new ArrayList<>();

        if (current.getNetRevenue() == null || current.getNetRevenue().compareTo(BigDecimal.ZERO) <= 0) {
            warnings.add("Doanh thu thuan khong duong, can kiem tra doanh thu ghi nhan va cac khoan giam tru.");
        }

        if (STATUS_WARNING.equals(cogsRatioStatus)) {
            warnings.add(String.format(
                    "Ty le gia von/doanh thu thuan dang o muc %s%%, vuot nguong canh bao.",
                    toDisplayNumber(cogsRatio)));
        }

        if (STATUS_WARNING.equals(returnRatioStatus)) {
            warnings.add(String.format(
                    "Ty le hang tra lai dang o muc %s%%, can ra soat chat luong don hang va van hanh giao nhan.",
                    toDisplayNumber(returnRatio)));
        }

        if (safe(current.getNetProfit()).compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("Loi nhuan rong dang am trong ky hien tai.");
        }

        if (hasBaselineData(previous) && safe(current.getNetProfit()).compareTo(safe(previous.getNetProfit())) < 0) {
            warnings.add("Loi nhuan rong giam so voi ky truoc.");
        }

        return warnings;
    }

    private boolean shouldWarnOnCogsRatio(ProfitLossResponse current, BigDecimal cogsRatio) {
        if (safe(current.getNetRevenue()).compareTo(BigDecimal.ZERO) <= 0) {
            return safe(current.getCogs()).compareTo(BigDecimal.ZERO) > 0;
        }
        return cogsRatio.compareTo(settingService.getPLCOGSWarningThreshold()) >= 0;
    }

    private boolean shouldWarnOnReturnRatio(ProfitLossResponse current, BigDecimal returnRatio) {
        if (safe(current.getGrossRevenue()).compareTo(BigDecimal.ZERO) <= 0) {
            return safe(current.getReturnedGoods()).compareTo(BigDecimal.ZERO) > 0;
        }
        return returnRatio.compareTo(settingService.getPLReturnWarningThreshold()) >= 0;
    }

    private String formatNetProfitChangePercent(
            BigDecimal currentNetProfit,
            BigDecimal previousNetProfit,
            ProfitLossResponse previous) {
        BigDecimal current = safe(currentNetProfit);
        BigDecimal previousValue = safe(previousNetProfit);

        if (!hasBaselineData(previous)) {
            return NO_PREVIOUS_DATA;
        }

        if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
            if (current.compareTo(BigDecimal.ZERO) == 0) {
                return "0.0";
            }
            return current.compareTo(BigDecimal.ZERO) > 0 ? "100.0" : "-100.0";
        }

        BigDecimal change = current.subtract(previousValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousValue.abs(), 1, RoundingMode.HALF_UP);
        return change.stripTrailingZeros().toPlainString();
    }

    private BigDecimal calculateRatioPercent(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal safeDenominator = safe(denominator);
        if (safeDenominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }

        return safe(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(safeDenominator, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal netShippingImpact(ProfitLossResponse response) {
        return safe(response.getShippingFeeCollected()).subtract(safe(response.getShippingFeeReturned()));
    }

    private BigDecimal netDiscountImpact(ProfitLossResponse response) {
        return safe(response.getDiscountReturned()).subtract(safe(response.getDiscount()));
    }

    private boolean hasBaselineData(ProfitLossResponse report) {
        return safe(report.getGrossRevenue()).compareTo(BigDecimal.ZERO) != 0
                || safe(report.getReturnedGoods()).compareTo(BigDecimal.ZERO) != 0
                || safe(report.getNetRevenue()).compareTo(BigDecimal.ZERO) != 0
                || safe(report.getCogs()).compareTo(BigDecimal.ZERO) != 0
                || safe(report.getNetProfit()).compareTo(BigDecimal.ZERO) != 0;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toDisplayNumber(BigDecimal value) {
        return safe(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}

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
    // Kỳ trước có phát sinh (hasBaselineData=true) nhưng lợi nhuận ròng kỳ trước đúng bằng 0 —
    // không thể chia cho 0 để ra % thật, nên trả sentinel thay vì bịa "+100%"/"-100%" như trước.
    private static final String NEW_BASELINE = "NEW_BASELINE";

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
                "Doanh thu gốc tiền hàng được ghi nhận trong kỳ."));
        items.add(buildItem(
                "COGS",
                safe(current.getCogs()),
                safe(previous.getCogs()),
                "Giá vốn hàng bán đã ghi nhận theo các đơn đã phát sinh doanh thu."));
        items.add(buildItem(
                "SHIPPING",
                netShippingImpact(current),
                netShippingImpact(previous),
                "Tác động ròng từ phí ship thu khách trừ phí ship hoàn trả."));
        items.add(buildItem(
                "DISCOUNT",
                netDiscountImpact(current),
                netDiscountImpact(previous),
                "Tác động ròng từ chiết khấu sau khi cộng lại phần được hoàn."));
        items.add(buildItem(
                "RETURNS",
                safe(current.getReturnedGoods()),
                safe(previous.getReturnedGoods()),
                "Giá trị hàng bán bị trả lại làm giảm hiệu quả lợi nhuận."));
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
            warnings.add("Doanh thu thuần không dương, cần kiểm tra doanh thu ghi nhận và các khoản giảm trừ.");
        }

        if (STATUS_WARNING.equals(cogsRatioStatus)) {
            warnings.add(String.format(
                    "Tỷ lệ giá vốn/doanh thu thuần đang ở mức %s%%, vượt ngưỡng cảnh báo.",
                    toDisplayNumber(cogsRatio)));
        }

        if (STATUS_WARNING.equals(returnRatioStatus)) {
            warnings.add(String.format(
                    "Tỷ lệ hàng trả lại đang ở mức %s%%, cần rà soát chất lượng đơn hàng và vận hành giao nhận.",
                    toDisplayNumber(returnRatio)));
        }

        if (safe(current.getNetProfit()).compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("Lợi nhuận ròng đang âm trong kỳ hiện tại.");
        }

        if (hasBaselineData(previous) && safe(current.getNetProfit()).compareTo(safe(previous.getNetProfit())) < 0) {
            warnings.add("Lợi nhuận ròng giảm so với kỳ trước.");
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
            return current.compareTo(BigDecimal.ZERO) == 0 ? "0.0" : NEW_BASELINE;
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

package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.dto.response.financial.ProfitLossInsightResponse;
import com.zone.agri.dto.response.financial.ProfitLossInsightResponse.ContributionBreakdownItem;
import com.zone.agri.dto.response.financial.ProfitLossResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfitLossInsightService {

    private final FinancialService financialService;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    public ProfitLossInsightResponse getProfitLossInsights(
            LocalDate startDate,
            LocalDate endDate,
            Long branchId) {

        log.info("Calculating profit-loss insights for branchId: {}, startDate: {}, endDate: {}", branchId, startDate, endDate);

        // 1. Calculate current period report
        ProfitLossResponse currentReport = financialService.getProfitLossReport(startDate, endDate, branchId);

        // 2. Check if we have previous period
        boolean hasPreviousPeriod = startDate != null && startDate.isAfter(LocalDate.of(2000, 1, 1));
        ProfitLossResponse previousReport = null;

        if (hasPreviousPeriod) {
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            LocalDate prevEnd = startDate.minusDays(1);
            LocalDate prevStart = prevEnd.minusDays(days);
            previousReport = financialService.getProfitLossReport(prevStart, prevEnd, branchId);
        }

        // 3. Calculate netProfitChangePercent
        Object netProfitChangePercent;
        if (previousReport == null) {
            netProfitChangePercent = "NO_PREVIOUS_DATA";
        } else {
            BigDecimal prevNetProfit = previousReport.getNetProfit();
            BigDecimal currNetProfit = currentReport.getNetProfit();
            if (prevNetProfit == null || prevNetProfit.compareTo(BigDecimal.ZERO) == 0) {
                netProfitChangePercent = currNetProfit != null && currNetProfit.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
            } else {
                BigDecimal diff = currNetProfit.subtract(prevNetProfit);
                BigDecimal percent = diff.multiply(BigDecimal.valueOf(100))
                        .divide(prevNetProfit.abs(), 2, RoundingMode.HALF_UP);
                netProfitChangePercent = percent.doubleValue();
            }
        }

        // 4. Calculate ratio metrics
        double cogsRatio = 0.0;
        if (currentReport.getNetRevenue() != null && currentReport.getNetRevenue().compareTo(BigDecimal.ZERO) > 0) {
            cogsRatio = currentReport.getCogs()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(currentReport.getNetRevenue(), 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        double returnRatio = 0.0;
        if (currentReport.getGrossRevenue() != null && currentReport.getGrossRevenue().compareTo(BigDecimal.ZERO) > 0) {
            returnRatio = currentReport.getReturnedGoods()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(currentReport.getGrossRevenue(), 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // 5. Evaluate thresholds and status
        BigDecimal cogsThreshold = settingService.getPLCOGSWarningThreshold();
        BigDecimal returnThreshold = settingService.getPLReturnWarningThreshold();

        String cogsRatioStatus = cogsRatio >= cogsThreshold.doubleValue() ? "WARNING" : "NORMAL";
        String returnRatioStatus = returnRatio >= returnThreshold.doubleValue() ? "WARNING" : "NORMAL";

        // 6. Build warnings list
        List<String> warnings = new ArrayList<>();
        if ("WARNING".equals(cogsRatioStatus)) {
            warnings.add("Tỷ lệ giá vốn/doanh thu thuần vượt ngưỡng cảnh báo (" + cogsThreshold + "%). Biên lợi nhuận gộp đang bị thu hẹp.");
        }
        if ("WARNING".equals(returnRatioStatus)) {
            warnings.add("Tỷ lệ hàng bán bị trả lại vượt ngưỡng cảnh báo (" + returnThreshold + "%). Cần rà soát chất lượng sản phẩm hoặc dịch vụ.");
        }

        boolean isNetProfitNegative = currentReport.getNetProfit() != null && currentReport.getNetProfit().compareTo(BigDecimal.ZERO) < 0;
        if (isNetProfitNegative) {
            warnings.add("Cửa hàng đang ghi nhận lợi nhuận ròng âm trong kỳ này (" + formatVND(currentReport.getNetProfit()) + "). Cần tối ưu chi phí và tăng doanh số.");
        }

        // 7. Calculate contributionBreakdown
        List<ContributionBreakdownItem> breakdown = new ArrayList<>();

        // REVENUE: Gross Revenue
        breakdown.add(createBreakdownItem(
                "REVENUE",
                currentReport.getGrossRevenue(),
                previousReport != null ? previousReport.getGrossRevenue() : null,
                "Doanh thu gốc từ tiền hàng (trước chiết khấu và trả lại)"
        ));

        // COGS: Cost of Goods Sold
        breakdown.add(createBreakdownItem(
                "COGS",
                currentReport.getCogs(),
                previousReport != null ? previousReport.getCogs() : null,
                "Giá vốn hàng bán phát sinh trong kỳ"
        ));

        // SHIPPING: shipping fee collected - shipping fee returned
        BigDecimal currShip = getSafeBigDecimal(currentReport.getShippingFeeCollected()).subtract(getSafeBigDecimal(currentReport.getShippingFeeReturned()));
        BigDecimal prevShip = previousReport != null 
                ? getSafeBigDecimal(previousReport.getShippingFeeCollected()).subtract(getSafeBigDecimal(previousReport.getShippingFeeReturned()))
                : null;
        breakdown.add(createBreakdownItem(
                "SHIPPING",
                currShip,
                prevShip,
                "Thu phí vận chuyển ròng (phí ship thu khách trừ phí ship hoàn trả)"
        ));

        // DISCOUNT: discount - discount returned
        BigDecimal currDiscount = getSafeBigDecimal(currentReport.getDiscount()).subtract(getSafeBigDecimal(currentReport.getDiscountReturned()));
        BigDecimal prevDiscount = previousReport != null 
                ? getSafeBigDecimal(previousReport.getDiscount()).subtract(getSafeBigDecimal(previousReport.getDiscountReturned()))
                : null;
        breakdown.add(createBreakdownItem(
                "DISCOUNT",
                currDiscount,
                prevDiscount,
                "Tổng tiền chiết khấu giảm giá ròng áp dụng cho đơn hàng"
        ));

        // RETURNS: returned goods
        breakdown.add(createBreakdownItem(
                "RETURNS",
                currentReport.getReturnedGoods(),
                previousReport != null ? previousReport.getReturnedGoods() : null,
                "Tổng giá trị hàng bán bị trả lại từ khách hàng"
        ));

        List<String> excludedZeroFields = List.of("pointPayment", "shippingFeePaid", "otherIncome", "customerReturnFee", "otherExpenses");

        return ProfitLossInsightResponse.builder()
                .netProfitChangePercent(netProfitChangePercent)
                .contributionBreakdown(breakdown)
                .cogsRatio(cogsRatio)
                .cogsRatioStatus(cogsRatioStatus)
                .returnRatio(returnRatio)
                .returnRatioStatus(returnRatioStatus)
                .isNetProfitNegative(isNetProfitNegative)
                .excludedZeroFields(excludedZeroFields)
                .warnings(warnings)
                .build();
    }

    private ContributionBreakdownItem createBreakdownItem(String factor, BigDecimal currentVal, BigDecimal prevVal, String baseDesc) {
        BigDecimal change = null;
        String note;

        BigDecimal safeCurrent = getSafeBigDecimal(currentVal);
        
        if (prevVal != null) {
            BigDecimal safePrev = getSafeBigDecimal(prevVal);
            change = safeCurrent.subtract(safePrev);
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                note = baseDesc + " tăng " + formatVND(change) + " so với kỳ trước.";
            } else if (change.compareTo(BigDecimal.ZERO) < 0) {
                note = baseDesc + " giảm " + formatVND(change.abs()) + " so với kỳ trước.";
            } else {
                note = baseDesc + " ổn định so với kỳ trước.";
            }
        } else {
            note = baseDesc + " đạt " + formatVND(safeCurrent) + " trong kỳ.";
        }

        return ContributionBreakdownItem.builder()
                .factor(factor)
                .currentValue(safeCurrent)
                .previousValue(prevVal)
                .changeAmount(change)
                .note(note)
                .build();
    }

    private BigDecimal getSafeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatVND(BigDecimal value) {
        if (value == null) return "0đ";
        return new java.text.DecimalFormat("#,###đ").format(value);
    }
}

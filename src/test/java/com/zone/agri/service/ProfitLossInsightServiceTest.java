package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.dto.response.financial.ProfitLossInsightResponse;
import com.zone.agri.dto.response.financial.ProfitLossInsightResponse.ContributionBreakdownItem;
import com.zone.agri.dto.response.financial.ProfitLossResponse;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ProfitLossInsightServiceTest {

    @Mock
    private FinancialService financialService;

    @Mock
    private SettingService settingService;

    @InjectMocks
    private ProfitLossInsightService profitLossInsightService;

    @BeforeEach
    void setUp() {
        when(settingService.getPLCOGSWarningThreshold()).thenReturn(BigDecimal.valueOf(75.0));
        when(settingService.getPLReturnWarningThreshold()).thenReturn(BigDecimal.valueOf(10.0));
    }

    @Test
    void getProfitLossInsights_shouldCalculateCorrectlyWhenPreviousPeriodExists() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 10); // 9 days diff

        ProfitLossResponse currentReport = ProfitLossResponse.builder()
                .grossRevenue(BigDecimal.valueOf(10000000))
                .returnedGoods(BigDecimal.valueOf(500000))
                .shippingFeeCollected(BigDecimal.valueOf(100000))
                .shippingFeeReturned(BigDecimal.valueOf(20000))
                .discount(BigDecimal.valueOf(300000))
                .discountReturned(BigDecimal.valueOf(10000))
                .netRevenue(BigDecimal.valueOf(9290000)) // 10M - 500k + 100k - 20k - 300k + 10k
                .cogs(BigDecimal.valueOf(6000000))
                .netProfit(BigDecimal.valueOf(3290000))
                .build();

        // 9 days diff -> prevEnd = 2026-06-30, prevStart = 2026-06-21
        ProfitLossResponse previousReport = ProfitLossResponse.builder()
                .grossRevenue(BigDecimal.valueOf(8000000))
                .returnedGoods(BigDecimal.valueOf(400000))
                .shippingFeeCollected(BigDecimal.valueOf(80000))
                .shippingFeeReturned(BigDecimal.valueOf(10000))
                .discount(BigDecimal.valueOf(200000))
                .discountReturned(BigDecimal.valueOf(5000))
                .netRevenue(BigDecimal.valueOf(7475000))
                .cogs(BigDecimal.valueOf(5000000))
                .netProfit(BigDecimal.valueOf(2475000))
                .build();

        when(financialService.getProfitLossReport(eq(start), eq(end), eq(1L))).thenReturn(currentReport);
        when(financialService.getProfitLossReport(eq(LocalDate.of(2026, 6, 21)), eq(LocalDate.of(2026, 6, 30)), eq(1L)))
                .thenReturn(previousReport);

        ProfitLossInsightResponse response = profitLossInsightService.getProfitLossInsights(start, end, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getNetProfitChangePercent()).isEqualTo(32.93); // (3.29M - 2.475M) / 2.475M * 100%
        assertThat(response.getCogsRatio()).isEqualTo(64.59); // 6M / 9.29M * 100%
        assertThat(response.getReturnRatio()).isEqualTo(5.0); // 500k / 10M * 100%
        assertThat(response.getCogsRatioStatus()).isEqualTo("NORMAL");
        assertThat(response.getReturnRatioStatus()).isEqualTo("NORMAL");
        assertThat(response.isNetProfitNegative()).isFalse();

        // Verify contributionBreakdown
        List<ContributionBreakdownItem> breakdown = response.getContributionBreakdown();
        assertThat(breakdown).hasSize(5);

        // Check REVENUE item details
        ContributionBreakdownItem revenueItem = breakdown.stream()
                .filter(item -> "REVENUE".equals(item.getFactor()))
                .findFirst().orElseThrow();
        assertThat(revenueItem.getCurrentValue()).isEqualByComparingTo("10000000");
        assertThat(revenueItem.getPreviousValue()).isEqualByComparingTo("8000000");
        assertThat(revenueItem.getChangeAmount()).isEqualByComparingTo("2000000");
    }

    @Test
    void getProfitLossInsights_shouldSetNoPreviousDataWhenPreviousPeriodIsNull() {
        LocalDate start = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.of(2000, 1, 10);

        ProfitLossResponse currentReport = ProfitLossResponse.builder()
                .grossRevenue(BigDecimal.valueOf(5000000))
                .returnedGoods(BigDecimal.valueOf(100000))
                .shippingFeeCollected(BigDecimal.valueOf(50000))
                .shippingFeeReturned(BigDecimal.valueOf(5000))
                .discount(BigDecimal.valueOf(100000))
                .discountReturned(BigDecimal.valueOf(1000))
                .netRevenue(BigDecimal.valueOf(4846000))
                .cogs(BigDecimal.valueOf(3000000))
                .netProfit(BigDecimal.valueOf(1846000))
                .build();

        when(financialService.getProfitLossReport(eq(start), eq(end), eq(1L))).thenReturn(currentReport);

        ProfitLossInsightResponse response = profitLossInsightService.getProfitLossInsights(start, end, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getNetProfitChangePercent()).isEqualTo("NO_PREVIOUS_DATA");

        // Verify that previousValue and changeAmount are null for all items in breakdown
        List<ContributionBreakdownItem> breakdown = response.getContributionBreakdown();
        for (ContributionBreakdownItem item : breakdown) {
            assertThat(item.getPreviousValue()).isNull();
            assertThat(item.getChangeAmount()).isNull();
        }
    }

    @Test
    void getProfitLossInsights_shouldSetNetProfitNegativeCorrectly() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 10);

        ProfitLossResponse currentReport = ProfitLossResponse.builder()
                .grossRevenue(BigDecimal.valueOf(1000000))
                .returnedGoods(BigDecimal.valueOf(100000))
                .shippingFeeCollected(BigDecimal.valueOf(10000))
                .shippingFeeReturned(BigDecimal.valueOf(1000))
                .discount(BigDecimal.valueOf(20000))
                .discountReturned(BigDecimal.valueOf(1000))
                .netRevenue(BigDecimal.valueOf(890000))
                .cogs(BigDecimal.valueOf(1500000)) // COGS > NetRevenue
                .netProfit(BigDecimal.valueOf(-610000)) // Negative Net Profit
                .build();

        when(financialService.getProfitLossReport(eq(start), eq(end), eq(1L))).thenReturn(currentReport);

        ProfitLossInsightResponse response = profitLossInsightService.getProfitLossInsights(start, end, 1L);

        assertThat(response).isNotNull();
        assertThat(response.isNetProfitNegative()).isTrue();
        assertThat(response.getWarnings()).contains("Cửa hàng đang ghi nhận lợi nhuận ròng âm trong kỳ này (-610,000đ). Cần tối ưu chi phí và tăng doanh số.");
    }

    @Test
    void getProfitLossInsights_shouldTriggerWarningWhenThresholdsAreExceeded() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 10);

        ProfitLossResponse currentReport = ProfitLossResponse.builder()
                .grossRevenue(BigDecimal.valueOf(10000000))
                .returnedGoods(BigDecimal.valueOf(1500000)) // 15% > 10% threshold
                .shippingFeeCollected(BigDecimal.valueOf(100000))
                .shippingFeeReturned(BigDecimal.valueOf(20000))
                .discount(BigDecimal.valueOf(300000))
                .discountReturned(BigDecimal.valueOf(10000))
                .netRevenue(BigDecimal.valueOf(8290000))
                .cogs(BigDecimal.valueOf(6700000)) // 6.7M / 8.29M = 80.8% > 75% threshold
                .netProfit(BigDecimal.valueOf(1590000))
                .build();

        when(financialService.getProfitLossReport(eq(start), eq(end), eq(1L))).thenReturn(currentReport);

        ProfitLossInsightResponse response = profitLossInsightService.getProfitLossInsights(start, end, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getCogsRatioStatus()).isEqualTo("WARNING");
        assertThat(response.getReturnRatioStatus()).isEqualTo("WARNING");
        assertThat(response.getWarnings()).hasSize(2);
        assertThat(response.getWarnings().get(0)).contains("Tỷ lệ giá vốn/doanh thu thuần vượt ngưỡng cảnh báo (75.0%). Biên lợi nhuận gộp đang bị thu hẹp.");
        assertThat(response.getWarnings().get(1)).contains("Tỷ lệ hàng bán bị trả lại vượt ngưỡng cảnh báo (10.0%). Cần rà soát chất lượng sản phẩm hoặc dịch vụ.");
    }
}

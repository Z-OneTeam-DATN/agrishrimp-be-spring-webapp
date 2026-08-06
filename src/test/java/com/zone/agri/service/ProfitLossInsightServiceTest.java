package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.dto.response.financial.ProfitLossInsightResponse;
import com.zone.agri.dto.response.financial.ProfitLossResponse;

@ExtendWith(MockitoExtension.class)
class ProfitLossInsightServiceTest {

    @Mock
    private FinancialService financialService;

    @Mock
    private SettingService settingService;

    @InjectMocks
    private ProfitLossInsightService profitLossInsightService;

    @Test
    void getProfitLossInsights_shouldBuildRatiosWarningsAndContributionBreakdown() {
        when(settingService.getPLCOGSWarningThreshold()).thenReturn(new BigDecimal("70"));
        when(settingService.getPLReturnWarningThreshold()).thenReturn(new BigDecimal("8"));

        when(financialService.getProfitLossReport(
                eq(LocalDate.of(2026, 7, 1)),
                eq(LocalDate.of(2026, 7, 31)),
                eq(3L)))
                .thenReturn(report(
                        "1000", "150", "80", "20", "50", "5",
                        "745", "755", "600", "-5"));

        when(financialService.getProfitLossReport(
                eq(LocalDate.of(2026, 5, 31)),
                eq(LocalDate.of(2026, 6, 30)),
                eq(3L)))
                .thenReturn(report(
                        "900", "100", "70", "10", "40", "5",
                        "800", "815", "500", "120"));

        ProfitLossInsightResponse response = profitLossInsightService.getProfitLossInsights(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                3L);

        assertThat(response.getCogsRatio()).isEqualByComparingTo("79.5");
        assertThat(response.getCogsRatioStatus()).isEqualTo("WARNING");
        assertThat(response.getReturnRatio()).isEqualByComparingTo("15.0");
        assertThat(response.getReturnRatioStatus()).isEqualTo("WARNING");
        assertThat(response.getNetProfitChangePercent()).isEqualTo("-104.2");
        assertThat(response.getContributionBreakdown()).hasSize(5);
        assertThat(response.getContributionBreakdown().get(2).getFactor()).isEqualTo("SHIPPING");
        assertThat(response.getContributionBreakdown().get(2).getCurrentValue()).isEqualByComparingTo("60");
        assertThat(response.getContributionBreakdown().get(3).getCurrentValue()).isEqualByComparingTo("-45");
        assertThat(response.getWarnings()).isNotEmpty();
    }

    @Test
    void getProfitLossInsights_shouldReturnNoPreviousDataWhenBaselineIsEmpty() {
        when(settingService.getPLCOGSWarningThreshold()).thenReturn(new BigDecimal("70"));
        when(settingService.getPLReturnWarningThreshold()).thenReturn(new BigDecimal("8"));

        when(financialService.getProfitLossReport(
                eq(LocalDate.of(2026, 7, 1)),
                eq(LocalDate.of(2026, 7, 31)),
                eq(null)))
                .thenReturn(report(
                        "100", "0", "10", "0", "0", "0",
                        "100", "110", "20", "90"));

        when(financialService.getProfitLossReport(
                eq(LocalDate.of(2026, 5, 31)),
                eq(LocalDate.of(2026, 6, 30)),
                eq(null)))
                .thenReturn(report(
                        "0", "0", "0", "0", "0", "0",
                        "0", "0", "0", "0"));

        ProfitLossInsightResponse response = profitLossInsightService.getProfitLossInsights(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null);

        assertThat(response.getNetProfitChangePercent()).isEqualTo("NO_PREVIOUS_DATA");
        assertThat(response.getWarnings()).isEmpty();
    }

    private ProfitLossResponse report(
            String grossRevenue,
            String returnedGoods,
            String shippingFeeCollected,
            String shippingFeeReturned,
            String discount,
            String discountReturned,
            String netProductRevenue,
            String netRevenue,
            String cogs,
            String netProfit) {
        return ProfitLossResponse.builder()
                .grossRevenue(new BigDecimal(grossRevenue))
                .returnedGoods(new BigDecimal(returnedGoods))
                .shippingFeeCollected(new BigDecimal(shippingFeeCollected))
                .shippingFeeReturned(new BigDecimal(shippingFeeReturned))
                .discount(new BigDecimal(discount))
                .discountReturned(new BigDecimal(discountReturned))
                .netProductRevenue(new BigDecimal(netProductRevenue))
                .netRevenue(new BigDecimal(netRevenue))
                .cogs(new BigDecimal(cogs))
                .grossProfit(new BigDecimal(netRevenue).subtract(new BigDecimal(cogs)))
                .otherIncome(BigDecimal.ZERO)
                .customerReturnFee(BigDecimal.ZERO)
                .otherExpenses(BigDecimal.ZERO)
                .pointPayment(BigDecimal.ZERO)
                .shippingFeePaid(BigDecimal.ZERO)
                .netProfit(new BigDecimal(netProfit))
                .build();
    }
}

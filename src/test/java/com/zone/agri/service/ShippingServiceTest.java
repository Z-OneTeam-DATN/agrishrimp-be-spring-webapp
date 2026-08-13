package com.zone.agri.service;

import com.zone.agri.dto.request.geo.ShippingFeeParams;
import com.zone.agri.dto.response.geo.DeliveryInfo;
import com.zone.agri.dto.response.geo.ShippingFeeResult;
import com.zone.agri.dto.response.order.OrderItemDto;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.entity.ProductVariant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock
    private GHNShippingProvider ghnProvider;

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService(ghnProvider);

        ReflectionTestUtils.setField(shippingService, "shippingProviderName", "ghn");
        ReflectionTestUtils.setField(shippingService, "defaultWeightGram", 500);
        ReflectionTestUtils.setField(shippingService, "fallbackBaseFee", new BigDecimal("15000"));
        ReflectionTestUtils.setField(shippingService, "fallbackPerKmFee", new BigDecimal("3000"));
        ReflectionTestUtils.setField(shippingService, "fallbackExtraWeightThresholdGram", 1000);
        ReflectionTestUtils.setField(shippingService, "fallbackExtraWeightStepGram", 1000);
        ReflectionTestUtils.setField(shippingService, "fallbackExtraWeightFee", new BigDecimal("5000"));
        ReflectionTestUtils.setField(shippingService, "fallbackMinFee", new BigDecimal("20000"));
        ReflectionTestUtils.setField(shippingService, "fallbackMaxFee", new BigDecimal("80000"));
    }

    @Test
    void enrichWithShippingFees_whenGhnSuccessUsesProviderFeeAndRequiredPayload() {
        when(ghnProvider.calculateFee(any())).thenReturn(ghnFee("34000"));

        SubOrderDraftDto result = enrichOne(
                draft(1L, 3695, 3d, List.of(item(101L, 1, "30000"))),
                delivery(2090, "22407"),
                Map.of(101L, variant(101L, null))
        );

        ArgumentCaptor<ShippingFeeParams> paramsCaptor = ArgumentCaptor.forClass(ShippingFeeParams.class);
        org.mockito.Mockito.verify(ghnProvider).calculateFee(paramsCaptor.capture());
        ShippingFeeParams params = paramsCaptor.getValue();

        assertThat(params.getFromDistrictId()).isEqualTo(3695);
        assertThat(params.getToDistrictId()).isEqualTo(2090);
        assertThat(params.getToWardCode()).isEqualTo("22407");
        assertThat(params.getWeightGram()).isEqualTo(500);
        assertThat(params.getCodAmount()).isEqualTo(30000L);
        assertThat(result.getShippingFee()).isEqualByComparingTo("34000");
        assertThat(result.isShippingEstimate()).isFalse();
        assertThat(result.getShippingWeightGram()).isEqualTo(500);
        assertThat(result.getShippingEstimateReason()).isNull();
    }

    @Test
    void enrichWithShippingFees_whenMissingBranchDistrictFallsBackWithReason() {
        when(ghnProvider.calculateFee(any()))
                .thenThrow(new RuntimeException("fromDistrictId null - branch is missing GHN district id"));

        SubOrderDraftDto result = enrichOne(
                draft(1L, null, 3d, List.of(item(101L, 1, "30000"))),
                delivery(2090, "22407"),
                Map.of(101L, variant(101L, null))
        );

        assertThat(result.getShippingFee()).isEqualByComparingTo("24000");
        assertThat(result.isShippingEstimate()).isTrue();
        assertThat(result.getShippingEstimateReason()).isEqualTo("GHN_MISSING_BRANCH_DISTRICT");
    }

    @Test
    void enrichWithShippingFees_whenMissingDeliveryDistrictFallsBackWithReason() {
        when(ghnProvider.calculateFee(any()))
                .thenThrow(new RuntimeException("toDistrictId null - delivery address is missing district id"));

        SubOrderDraftDto result = enrichOne(
                draft(1L, 3695, 3d, List.of(item(101L, 1, "30000"))),
                delivery(null, "22407"),
                Map.of(101L, variant(101L, null))
        );

        assertThat(result.getShippingFee()).isEqualByComparingTo("24000");
        assertThat(result.isShippingEstimate()).isTrue();
        assertThat(result.getShippingEstimateReason()).isEqualTo("GHN_MISSING_DELIVERY_DISTRICT");
    }

    @Test
    void enrichWithShippingFees_whenMissingDeliveryWardFallsBackWithReason() {
        when(ghnProvider.calculateFee(any()))
                .thenThrow(new RuntimeException("toWardCode null - delivery address is missing GHN ward code"));

        SubOrderDraftDto result = enrichOne(
                draft(1L, 3695, 3d, List.of(item(101L, 1, "30000"))),
                delivery(2090, null),
                Map.of(101L, variant(101L, null))
        );

        assertThat(result.getShippingFee()).isEqualByComparingTo("24000");
        assertThat(result.isShippingEstimate()).isTrue();
        assertThat(result.getShippingEstimateReason()).isEqualTo("GHN_MISSING_DELIVERY_WARD");
    }

    @Test
    void enrichWithShippingFees_whenGhnApiFailsFallsBackWithApiReason() {
        when(ghnProvider.calculateFee(any()))
                .thenThrow(new RuntimeException("GHN API error code=400 message=no service"));

        SubOrderDraftDto result = enrichOne(
                draft(1L, 3695, 3d, List.of(item(101L, 1, "30000"))),
                delivery(2090, "22407"),
                Map.of(101L, variant(101L, null))
        );

        assertThat(result.getShippingFee()).isEqualByComparingTo("24000");
        assertThat(result.isShippingEstimate()).isTrue();
        assertThat(result.getShippingEstimateReason()).isEqualTo("GHN_API_FAILED");
    }

    @Test
    void enrichWithShippingFees_whenVariantHasNoWeightUsesDefaultWeightPerQuantity() {
        when(ghnProvider.calculateFee(any())).thenReturn(ghnFee("34000"));

        SubOrderDraftDto result = enrichOne(
                draft(1L, 3695, 3d, List.of(item(101L, 3, "30000"))),
                delivery(2090, "22407"),
                Map.of(101L, variant(101L, null))
        );

        ArgumentCaptor<ShippingFeeParams> paramsCaptor = ArgumentCaptor.forClass(ShippingFeeParams.class);
        org.mockito.Mockito.verify(ghnProvider).calculateFee(paramsCaptor.capture());

        assertThat(paramsCaptor.getValue().getWeightGram()).isEqualTo(1500);
        assertThat(result.getShippingWeightGram()).isEqualTo(1500);
    }

    @Test
    void enrichWithShippingFees_whenVariantHasCustomWeightUsesCustomWeightPerQuantity() {
        when(ghnProvider.calculateFee(any())).thenReturn(ghnFee("34000"));

        SubOrderDraftDto result = enrichOne(
                draft(1L, 3695, 3d, List.of(item(101L, 2, "30000"))),
                delivery(2090, "22407"),
                Map.of(101L, variant(101L, "1200"))
        );

        ArgumentCaptor<ShippingFeeParams> paramsCaptor = ArgumentCaptor.forClass(ShippingFeeParams.class);
        org.mockito.Mockito.verify(ghnProvider).calculateFee(paramsCaptor.capture());

        assertThat(paramsCaptor.getValue().getWeightGram()).isEqualTo(2400);
        assertThat(result.getShippingWeightGram()).isEqualTo(2400);
    }

    @Test
    void buildFallbackQuote_appliesMinimumFee() {
        ShippingFeeResult result = shippingService.buildFallbackQuote(500, 0d, "TEST");

        assertThat(result.getTotalFee()).isEqualByComparingTo("20000");
        assertThat(result.isEstimate()).isTrue();
        assertThat(result.getEstimateReason()).isEqualTo("TEST");
    }

    @Test
    void buildFallbackQuote_appliesDistanceAndWeightSurcharge() {
        ShippingFeeResult result = shippingService.buildFallbackQuote(2500, 5d, "TEST");

        assertThat(result.getTotalFee()).isEqualByComparingTo("40000");
    }

    @Test
    void buildFallbackQuote_capsVeryLargeFallbackFees() {
        ShippingFeeResult result = shippingService.buildFallbackQuote(20000, 100d, "TEST");

        assertThat(result.getTotalFee()).isEqualByComparingTo("80000");
    }

    @Test
    void enrichWithShippingFees_whenMultipleSubOrdersSumsIndividualFees() {
        when(ghnProvider.calculateFee(any())).thenAnswer(invocation -> {
            ShippingFeeParams params = invocation.getArgument(0);
            if (Integer.valueOf(3695).equals(params.getFromDistrictId())) {
                return ghnFee("34000");
            }
            throw new RuntimeException("GHN API error code=500 message=temporary error");
        });

        List<SubOrderDraftDto> result = shippingService.enrichWithShippingFees(
                List.of(
                        draft(1L, 3695, 3d, List.of(item(101L, 1, "30000"))),
                        draft(2L, 3696, 5d, List.of(item(102L, 1, "45000")))
                ),
                delivery(2090, "22407"),
                Map.of(101L, variant(101L, null), 102L, variant(102L, null))
        );

        BigDecimal totalShippingFee = result.stream()
                .map(SubOrderDraftDto::getShippingFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(result).hasSize(2);
        assertThat(totalShippingFee).isEqualByComparingTo("64000");
        assertThat(result).anySatisfy(subOrder ->
                assertThat(subOrder.getShippingEstimateReason()).isEqualTo("GHN_API_FAILED"));
    }

    @Test
    void orderTotalFormulaAddsShippingThenSubtractsVoucherWithoutDiscountingShippingSeparately() {
        BigDecimal subtotal = new BigDecimal("100000");
        BigDecimal totalShippingFee = new BigDecimal("34000");
        BigDecimal discountAmount = new BigDecimal("20000");

        BigDecimal finalTotal = subtotal.add(totalShippingFee).subtract(discountAmount).max(BigDecimal.ZERO);

        assertThat(finalTotal).isEqualByComparingTo("114000");
    }

    @Test
    void legacyCheckoutFallbackNoLongerUsesFixed15000() {
        ShippingFeeResult result = shippingService.buildFallbackQuote(
                500,
                0d,
                "LEGACY_CHECKOUT_MISSING_GHN_ADDRESS"
        );

        assertThat(result.getTotalFee()).isEqualByComparingTo("20000");
        assertThat(result.getTotalFee()).isNotEqualByComparingTo("15000");
        assertThat(result.getEstimateReason()).isEqualTo("LEGACY_CHECKOUT_MISSING_GHN_ADDRESS");
    }

    private SubOrderDraftDto enrichOne(
            SubOrderDraftDto draft,
            DeliveryInfo deliveryInfo,
            Map<Long, ProductVariant> variantMap
    ) {
        return shippingService.enrichWithShippingFees(List.of(draft), deliveryInfo, variantMap).get(0);
    }

    private SubOrderDraftDto draft(Long branchId, Integer fromDistrictId, double distanceKm, List<OrderItemDto> items) {
        BigDecimal subtotal = items.stream()
                .map(OrderItemDto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SubOrderDraftDto.builder()
                .branchId(branchId)
                .branchName("Chi Nhanh " + branchId)
                .branchAddress("Dia chi chi nhanh " + branchId)
                .fromDistrictId(fromDistrictId)
                .distanceKm(distanceKm)
                .durationMinutes(distanceKm * 2)
                .items(items)
                .subtotal(subtotal)
                .build();
    }

    private OrderItemDto item(Long variantId, int quantity, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        return OrderItemDto.builder()
                .productVariantId(variantId)
                .variantName("San pham " + variantId)
                .variantSku("SKU-" + variantId)
                .quantity(quantity)
                .unitPrice(price)
                .subtotal(price.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    private DeliveryInfo delivery(Integer toDistrictId, String toWardCode) {
        return DeliveryInfo.builder()
                .toDistrictId(toDistrictId)
                .toWardCode(toWardCode)
                .deliveryAddress("Dia chi khach hang")
                .userLat(10.776889)
                .userLng(106.700806)
                .build();
    }

    private ProductVariant variant(Long id, String shippingWeightGram) {
        return ProductVariant.builder()
                .id(id)
                .sku("SKU-" + id)
                .shippingWeight(shippingWeightGram == null ? null : new BigDecimal(shippingWeightGram))
                .build();
    }

    private ShippingFeeResult ghnFee(String fee) {
        return ShippingFeeResult.builder()
                .totalFee(new BigDecimal(fee))
                .estimatedDays("2-3 ngay")
                .carrier("GHN")
                .isEstimate(false)
                .build();
    }
}

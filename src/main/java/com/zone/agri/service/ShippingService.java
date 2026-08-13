package com.zone.agri.service;

import com.zone.agri.dto.request.geo.ShippingFeeParams;
import com.zone.agri.dto.response.geo.DeliveryInfo;
import com.zone.agri.dto.response.geo.ShippingFeeResult;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.ShippingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final GHNShippingProvider ghnProvider;

    @Value("${shipping.provider:ghn}")
    private String shippingProviderName;

    @Value("${shipping.default-weight-gram:500}")
    private int defaultWeightGram;

    @Value("${shipping.fallback.base-fee:15000}")
    private BigDecimal fallbackBaseFee;

    @Value("${shipping.fallback.per-km-fee:3000}")
    private BigDecimal fallbackPerKmFee;

    @Value("${shipping.fallback.extra-weight-threshold-gram:1000}")
    private int fallbackExtraWeightThresholdGram;

    @Value("${shipping.fallback.extra-weight-step-gram:1000}")
    private int fallbackExtraWeightStepGram;

    @Value("${shipping.fallback.extra-weight-fee:5000}")
    private BigDecimal fallbackExtraWeightFee;

    @Value("${shipping.fallback.min-fee:20000}")
    private BigDecimal fallbackMinFee;

    @Value("${shipping.fallback.max-fee:80000}")
    private BigDecimal fallbackMaxFee;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public List<SubOrderDraftDto> enrichWithShippingFees(
            List<SubOrderDraftDto> subOrders,
            DeliveryInfo deliveryInfo,
            Map<Long, ProductVariant> variantMap
    ) {
        List<CompletableFuture<SubOrderDraftDto>> futures = subOrders.stream()
                .map(draft -> CompletableFuture
                        .supplyAsync(() -> calculateForDraft(draft, deliveryInfo, variantMap), executor)
                        .exceptionally(ex -> {
                            Throwable cause = unwrap(ex);
                            log.error("Shipping fee failed for branch {}: {}", draft.getBranchId(), cause.getMessage());
                            int weightGram = safeCalculateTotalWeightGram(draft, variantMap);
                            return buildFallbackDraft(draft, weightGram, resolveEstimateReason(cause));
                        })
                )
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<SubOrderDraftDto> result = new ArrayList<>();
        for (CompletableFuture<SubOrderDraftDto> f : futures) {
            try {
                result.add(f.get());
            } catch (Exception e) {
                log.error("Failed to get shipping future result: {}", e.getMessage());
            }
        }
        return result;
    }

    public ShippingFeeResult buildFallbackQuote(int totalWeightGram, double distanceKm, String reason) {
        return ShippingFeeResult.builder()
                .totalFee(calculateFallbackFee(totalWeightGram, distanceKm))
                .estimatedDays(resolveFallbackEstimatedDays(distanceKm))
                .carrier(resolveCarrierName())
                .isEstimate(true)
                .estimateReason(reason)
                .build();
    }

    public int resolveDefaultWeightGram() {
        return Math.max(1, defaultWeightGram);
    }

    private SubOrderDraftDto calculateForDraft(
            SubOrderDraftDto draft,
            DeliveryInfo deliveryInfo,
            Map<Long, ProductVariant> variantMap
    ) {
        int totalWeightGram = calculateTotalWeightGram(draft, variantMap);
        ShippingProvider provider = resolveProvider();
        ShippingFeeParams params = ShippingFeeParams.builder()
                .fromDistrictId(getFromDistrictId(draft))
                .toDistrictId(deliveryInfo.getToDistrictId())
                .toWardCode(deliveryInfo.getToWardCode())
                .weightGram(totalWeightGram)
                .codAmount(draft.getSubtotal() != null ? draft.getSubtotal().longValue() : 0L)
                .build();

        try {
            ShippingFeeResult result = provider.calculateFee(params);
            return draft.toBuilder()
                    .shippingFee(result.getTotalFee())
                    .shippingWeightGram(totalWeightGram)
                    .estimatedDays(result.getEstimatedDays())
                    .carrier(result.getCarrier())
                    .shippingEstimate(result.isEstimate())
                    .shippingEstimateReason(result.getEstimateReason())
                    .build();
        } catch (Exception ex) {
            String reason = resolveEstimateReason(ex);
            log.warn("GHN shipping fallback for branch {} because {}: {}",
                    draft.getBranchId(), reason, ex.getMessage());
            return buildFallbackDraft(draft, totalWeightGram, reason);
        }
    }

    private SubOrderDraftDto buildFallbackDraft(SubOrderDraftDto draft, int totalWeightGram, String reason) {
        ShippingFeeResult fallback = buildFallbackQuote(totalWeightGram, draft.getDistanceKm(), reason);
        return draft.toBuilder()
                .shippingFee(fallback.getTotalFee())
                .shippingWeightGram(totalWeightGram)
                .estimatedDays(fallback.getEstimatedDays())
                .carrier(fallback.getCarrier())
                .shippingEstimate(true)
                .shippingEstimateReason(fallback.getEstimateReason())
                .build();
    }

    private BigDecimal calculateFallbackFee(int totalWeightGram, double distanceKm) {
        BigDecimal distanceFee = BigDecimal.valueOf(Math.max(0d, distanceKm))
                .multiply(fallbackPerKmFee);

        int threshold = Math.max(0, fallbackExtraWeightThresholdGram);
        int stepGram = Math.max(1, fallbackExtraWeightStepGram);
        int extraWeightGram = Math.max(0, totalWeightGram - threshold);
        long extraWeightSteps = extraWeightGram == 0 ? 0 : (long) Math.ceil(extraWeightGram / (double) stepGram);
        BigDecimal weightFee = fallbackExtraWeightFee.multiply(BigDecimal.valueOf(extraWeightSteps));

        BigDecimal fee = fallbackBaseFee
                .add(distanceFee)
                .add(weightFee)
                .setScale(0, RoundingMode.CEILING);

        if (fee.compareTo(fallbackMinFee) < 0) {
            return fallbackMinFee.setScale(0, RoundingMode.CEILING);
        }
        if (fee.compareTo(fallbackMaxFee) > 0) {
            return fallbackMaxFee.setScale(0, RoundingMode.CEILING);
        }
        return fee;
    }

    private int safeCalculateTotalWeightGram(SubOrderDraftDto draft, Map<Long, ProductVariant> variantMap) {
        try {
            return calculateTotalWeightGram(draft, variantMap);
        } catch (Exception ignored) {
            return resolveDefaultWeightGram();
        }
    }

    private int calculateTotalWeightGram(SubOrderDraftDto draft, Map<Long, ProductVariant> variantMap) {
        if (draft.getItems() == null || draft.getItems().isEmpty()) {
            return resolveDefaultWeightGram();
        }

        long totalWeightGram = draft.getItems().stream()
                .mapToLong(item -> {
                    int quantity = Math.max(0, Objects.requireNonNullElse(item.getQuantity(), 0));
                    ProductVariant variant = variantMap != null ? variantMap.get(item.getProductVariantId()) : null;
                    return (long) resolveVariantWeightGram(variant) * quantity;
                })
                .sum();

        if (totalWeightGram <= 0) {
            return resolveDefaultWeightGram();
        }
        return (int) Math.min(Integer.MAX_VALUE, totalWeightGram);
    }

    private int resolveVariantWeightGram(ProductVariant variant) {
        if (variant != null
                && variant.getShippingWeight() != null
                && variant.getShippingWeight().compareTo(BigDecimal.ZERO) > 0) {
            return Math.max(1, variant.getShippingWeight().setScale(0, RoundingMode.CEILING).intValue());
        }
        return resolveDefaultWeightGram();
    }

    private String resolveFallbackEstimatedDays(double distanceKm) {
        return distanceKm <= 20d ? "1-2 ngay (uoc tinh)" : "2-3 ngay (uoc tinh)";
    }

    private String resolveEstimateReason(Throwable ex) {
        String message = ex != null && ex.getMessage() != null ? ex.getMessage() : "";
        if (message.contains("fromDistrictId")) {
            return "GHN_MISSING_BRANCH_DISTRICT";
        }
        if (message.contains("toDistrictId")) {
            return "GHN_MISSING_DELIVERY_DISTRICT";
        }
        if (message.contains("toWardCode")) {
            return "GHN_MISSING_DELIVERY_WARD";
        }
        return "GHN_API_FAILED";
    }

    private Throwable unwrap(Throwable ex) {
        if (ex instanceof CompletionException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
    }

    private Integer getFromDistrictId(SubOrderDraftDto draft) {
        return draft.getFromDistrictId();
    }

    private ShippingProvider resolveProvider() {
        return ghnProvider;
    }

    private String resolveCarrierName() {
        return shippingProviderName == null || shippingProviderName.isBlank()
                ? "GHN"
                : shippingProviderName.trim().toUpperCase();
    }
}

package com.zone.agri.service;

import com.zone.agri.dto.response.geo.DeliveryInfo;
import com.zone.agri.dto.request.geo.ShippingFeeParams;
import com.zone.agri.dto.response.geo.ShippingFeeResult;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.ShippingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final GHNShippingProvider ghnProvider;
    private final ProductVariantRepository productVariantRepository;

    @Value("${shipping.provider:ghn}")
    private String shippingProviderName;

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
                            log.error("Shipping fee failed for branch {}: {}", draft.getBranchId(), ex.getMessage());
                            return draft.toBuilder()
                                    .shippingFee(new BigDecimal("30000"))
                                    .estimatedDays("2-3 ngày (ước tính)")
                                    .carrier("GHN")
                                    .shippingEstimate(true)
                                    .build();
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

    private SubOrderDraftDto calculateForDraft(
            SubOrderDraftDto draft,
            DeliveryInfo deliveryInfo,
            Map<Long, ProductVariant> variantMap
    ) {
        // TÍNH TỔNG TRỌNG LƯỢNG (GRAM)
        int totalWeightGram = draft.getItems().stream()
                .mapToInt(item -> {
                    // Do hệ thống đã cấu trúc lại, xóa cột trọng lượng ở Variant.
                    // Tạm thời set mặc định 500 gram cho mỗi item để gọi API.
                    // Tương lai nếu có bảng Product lưu trọng lượng chung, bạn sẽ móc từ đó.
                    int defaultWeightPerItem = 500;
                    return defaultWeightPerItem * item.getQuantity();
                })
                .sum();

        if (totalWeightGram <= 0) totalWeightGram = 500;

        ShippingProvider provider = resolveProvider();
        ShippingFeeParams params = ShippingFeeParams.builder()
                .fromDistrictId(getFromDistrictId(draft))
                .toDistrictId(deliveryInfo.getToDistrictId())
                .toWardCode(deliveryInfo.getToWardCode())
                .weightGram(totalWeightGram)
                .codAmount(draft.getSubtotal() != null ? draft.getSubtotal().longValue() : 0L)
                .build();

        ShippingFeeResult result = provider.calculateFee(params);

        return draft.toBuilder()
                .shippingFee(result.getTotalFee())
                .estimatedDays(result.getEstimatedDays())
                .carrier(result.getCarrier())
                .shippingEstimate(result.isEstimate())
                .build();
    }

    private Integer getFromDistrictId(SubOrderDraftDto draft) {
        return draft.getFromDistrictId();
    }

    private ShippingProvider resolveProvider() {
        return ghnProvider;
    }
}
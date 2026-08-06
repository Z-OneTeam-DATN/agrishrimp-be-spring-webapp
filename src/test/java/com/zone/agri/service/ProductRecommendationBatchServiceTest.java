package com.zone.agri.service;

import com.zone.agri.entity.ProductRecommendation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRecommendationBatchServiceTest {

    private final ProductRecommendationBatchService service =
            new ProductRecommendationBatchService(null, null, null);

    @Test
    void calculateRecommendations_shouldComputeSupportConfidenceAndLift() {
        LocalDateTime calculatedAt = LocalDateTime.of(2026, 7, 12, 10, 0);

        List<ProductRecommendation> result = service.calculateRecommendations(List.of(
                row(1, 101, 11), row(1, 101, 22),
                row(2, 102, 11), row(2, 102, 22),
                row(3, 103, 11), row(3, 103, 22),
                row(4, 104, 11),
                row(5, 105, 33),
                row(6, 106, 33)
        ), calculatedAt);

        assertThat(result).hasSize(2);

        ProductRecommendation aToB = find(result, 11L, 22L);
        assertThat(aToB.getSupportCount()).isEqualTo(3);
        assertThat(aToB.getCustomerCount()).isEqualTo(3);
        assertThat(aToB.getSupport()).isEqualByComparingTo("0.500000");
        assertThat(aToB.getConfidence()).isEqualByComparingTo("0.750000");
        assertThat(aToB.getLift()).isEqualByComparingTo("1.500000");
        assertThat(aToB.getCalculatedAt()).isEqualTo(calculatedAt);

        ProductRecommendation bToA = find(result, 22L, 11L);
        assertThat(bToA.getSupportCount()).isEqualTo(3);
        assertThat(bToA.getCustomerCount()).isEqualTo(3);
        assertThat(bToA.getConfidence()).isEqualByComparingTo("1.000000");
        assertThat(bToA.getLift()).isEqualByComparingTo("1.500000");
    }

    @Test
    void calculateRecommendations_shouldRequireDistinctCustomersAlongsideSupportCount() {
        List<ProductRecommendation> result = service.calculateRecommendations(List.of(
                row(1, 101, 11), row(1, 101, 22),
                row(2, 101, 11), row(2, 101, 22),
                row(3, 101, 11), row(3, 101, 22),
                row(4, 102, 11),
                row(5, 103, 33),
                row(6, 104, 33)
        ), LocalDateTime.now());

        assertThat(result).isEmpty();
    }

    @Test
    void calculateRecommendations_shouldDeduplicateLegacyAndSubOrderRowsByOrderAndProduct() {
        List<ProductRecommendationBatchService.BasketItemRow> rows = List.of(
                row(1, 101, 11), row(1, 101, 11), row(1, 101, 22), row(1, 101, 22),
                row(2, 102, 11), row(2, 102, 11), row(2, 102, 22), row(2, 102, 22),
                row(3, 103, 11), row(3, 103, 11), row(3, 103, 22), row(3, 103, 22),
                row(4, 104, 11),
                row(5, 105, 33),
                row(6, 106, 33)
        );

        List<ProductRecommendation> result = service.calculateRecommendations(rows, LocalDateTime.now());

        assertThat(result).hasSize(2);
        assertThat(find(result, 11L, 22L).getSupportCount()).isEqualTo(3);
        assertThat(find(result, 22L, 11L).getSupportCount()).isEqualTo(3);
    }

    private ProductRecommendation find(List<ProductRecommendation> result, Long productId, Long recommendedProductId) {
        return result.stream()
                .filter(item -> item.getProductId().equals(productId)
                        && item.getRecommendedProductId().equals(recommendedProductId))
                .findFirst()
                .orElseThrow();
    }

    private ProductRecommendationBatchService.BasketItemRow row(long orderId, long customerId, long productId) {
        return new ProductRecommendationBatchService.BasketItemRow(orderId, customerId, productId);
    }
}

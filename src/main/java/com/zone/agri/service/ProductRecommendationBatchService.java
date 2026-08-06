package com.zone.agri.service;

import com.zone.agri.entity.ProductRecommendation;
import com.zone.agri.repository.ProductRecommendationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRecommendationBatchService {

    static final int DEFAULT_WINDOW_DAYS = 180;
    static final int MIN_SUPPORT_COUNT = 3;
    static final int MIN_CUSTOMER_COUNT = 3;
    static final double MIN_CONFIDENCE = 0.10;
    static final double MIN_LIFT = 1.0;
    static final int METRIC_SCALE = 6;

    private static final String RECOMMENDATION_CACHE_PATTERN = "product:frequently-bought-together:*";

    private final ProductRecommendationRepository productRecommendationRepository;
    private final EntityManager entityManager;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "${recommendations.batch.cron:0 15 3 * * ?}")
    @Transactional
    public void recomputeScheduled() {
        int savedCount = recompute(DEFAULT_WINDOW_DAYS);
        log.info("Product recommendation batch completed: saved {} rows", savedCount);
    }

    @Transactional
    public int recompute(int windowDays) {
        int safeWindowDays = windowDays > 0 ? windowDays : DEFAULT_WINDOW_DAYS;
        LocalDateTime startDate = LocalDateTime.now().minusDays(safeWindowDays);
        List<BasketItemRow> basketRows = fetchCompletedBasketRows(startDate);
        List<ProductRecommendation> recommendations = calculateRecommendations(basketRows, LocalDateTime.now());

        productRecommendationRepository.deleteAllInBatch();
        if (!recommendations.isEmpty()) {
            productRecommendationRepository.saveAll(recommendations);
        }
        clearRecommendationCache();
        return recommendations.size();
    }

    @SuppressWarnings("unchecked")
    private List<BasketItemRow> fetchCompletedBasketRows(LocalDateTime startDate) {
        Query query = entityManager.createNativeQuery("""
                SELECT DISTINCT basket.order_id, basket.user_id, basket.product_id
                FROM (
                    SELECT o.id AS order_id, o.user_id AS user_id, pv.product_id AS product_id
                    FROM orders o
                    JOIN order_items oi ON oi.order_id = o.id
                    JOIN product_variants pv ON pv.id = oi.product_variant_id
                    WHERE o.status = 'COMPLETED'
                      AND COALESCE(o.completed_at, o.created_at) >= :startDate
                    UNION
                    SELECT o.id AS order_id, o.user_id AS user_id, pv.product_id AS product_id
                    FROM sub_orders so
                    JOIN orders o ON o.id = so.order_id
                    JOIN sub_order_items soi ON soi.sub_order_id = so.id
                    JOIN product_variants pv ON pv.id = soi.product_variant_id
                    WHERE o.status = 'COMPLETED'
                      AND so.status = 'COMPLETED'
                      AND COALESCE(so.completed_at, so.created_at, o.completed_at, o.created_at) >= :startDate
                ) basket
                WHERE basket.product_id IS NOT NULL
                """);
        query.setParameter("startDate", startDate);

        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new BasketItemRow(
                        toLong(row[0]),
                        toLong(row[1]),
                        toLong(row[2])
                ))
                .filter(row -> row.orderId() != null && row.productId() != null)
                .toList();
    }

    List<ProductRecommendation> calculateRecommendations(
            List<BasketItemRow> basketRows,
            LocalDateTime calculatedAt
    ) {
        if (basketRows == null || basketRows.isEmpty()) {
            return List.of();
        }

        Map<Long, Basket> basketsByOrderId = new LinkedHashMap<>();
        for (BasketItemRow row : basketRows) {
            if (row == null || row.orderId() == null || row.productId() == null) {
                continue;
            }
            basketsByOrderId
                    .computeIfAbsent(row.orderId(), id -> new Basket(row.orderId(), row.customerId()))
                    .addProduct(row.productId());
        }

        int totalBasketCount = basketsByOrderId.size();
        if (totalBasketCount == 0) {
            return List.of();
        }

        Map<Long, Integer> productOrderCounts = new HashMap<>();
        Map<PairKey, PairStats> pairStats = new HashMap<>();

        for (Basket basket : basketsByOrderId.values()) {
            List<Long> productIds = basket.productIds().stream()
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
            if (productIds.isEmpty()) {
                continue;
            }

            productIds.forEach(productId -> productOrderCounts.merge(productId, 1, Integer::sum));

            if (productIds.size() < 2) {
                continue;
            }

            for (int i = 0; i < productIds.size() - 1; i++) {
                for (int j = i + 1; j < productIds.size(); j++) {
                    PairKey pairKey = new PairKey(productIds.get(i), productIds.get(j));
                    pairStats.computeIfAbsent(pairKey, ignored -> new PairStats())
                            .record(basket.customerId());
                }
            }
        }

        List<ProductRecommendation> recommendations = new ArrayList<>();
        for (Map.Entry<PairKey, PairStats> entry : pairStats.entrySet()) {
            PairKey pair = entry.getKey();
            PairStats stats = entry.getValue();

            addDirectedRecommendation(
                    recommendations,
                    pair.left(),
                    pair.right(),
                    stats,
                    productOrderCounts,
                    totalBasketCount,
                    calculatedAt
            );
            addDirectedRecommendation(
                    recommendations,
                    pair.right(),
                    pair.left(),
                    stats,
                    productOrderCounts,
                    totalBasketCount,
                    calculatedAt
            );
        }

        recommendations.sort(
                Comparator.comparing(ProductRecommendation::getProductId)
                        .thenComparing(ProductRecommendation::getLift, Comparator.reverseOrder())
                        .thenComparing(ProductRecommendation::getConfidence, Comparator.reverseOrder())
                        .thenComparing(ProductRecommendation::getRecommendedProductId)
        );
        return recommendations;
    }

    private void addDirectedRecommendation(
            List<ProductRecommendation> recommendations,
            Long productId,
            Long recommendedProductId,
            PairStats stats,
            Map<Long, Integer> productOrderCounts,
            int totalBasketCount,
            LocalDateTime calculatedAt
    ) {
        int supportCount = stats.orderCount();
        int customerCount = stats.customerCount();
        int anchorOrderCount = productOrderCounts.getOrDefault(productId, 0);
        int recommendedProductOrderCount = productOrderCounts.getOrDefault(recommendedProductId, 0);

        if (supportCount < MIN_SUPPORT_COUNT
                || customerCount < MIN_CUSTOMER_COUNT
                || anchorOrderCount == 0
                || recommendedProductOrderCount == 0) {
            return;
        }

        double support = (double) supportCount / totalBasketCount;
        double confidence = (double) supportCount / anchorOrderCount;
        double recommendedProductPopularity = (double) recommendedProductOrderCount / totalBasketCount;
        double lift = confidence / recommendedProductPopularity;

        if (confidence < MIN_CONFIDENCE || lift <= MIN_LIFT) {
            return;
        }

        recommendations.add(ProductRecommendation.builder()
                .productId(productId)
                .recommendedProductId(recommendedProductId)
                .supportCount(supportCount)
                .customerCount(customerCount)
                .support(toMetric(support))
                .confidence(toMetric(confidence))
                .lift(toMetric(lift))
                .calculatedAt(calculatedAt)
                .build());
    }

    private BigDecimal toMetric(double value) {
        return BigDecimal.valueOf(value).setScale(METRIC_SCALE, RoundingMode.HALF_UP);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private void clearRecommendationCache() {
        try {
            Set<String> keys = redisTemplate.keys(RECOMMENDATION_CACHE_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.debug("Skipped clearing product recommendation cache: {}", ex.getMessage());
        }
    }

    record BasketItemRow(Long orderId, Long customerId, Long productId) {
    }

    private record PairKey(Long left, Long right) {
    }

    private static final class Basket {
        private final Long orderId;
        private final Long customerId;
        private final Set<Long> productIds = new LinkedHashSet<>();

        private Basket(Long orderId, Long customerId) {
            this.orderId = orderId;
            this.customerId = customerId;
        }

        private void addProduct(Long productId) {
            productIds.add(productId);
        }

        private Long customerId() {
            return customerId;
        }

        private Set<Long> productIds() {
            return productIds;
        }

        @SuppressWarnings("unused")
        private Long orderId() {
            return orderId;
        }
    }

    private static final class PairStats {
        private int orderCount = 0;
        private final Set<Long> customerIds = new HashSet<>();

        private void record(Long customerId) {
            orderCount++;
            if (customerId != null) {
                customerIds.add(customerId);
            }
        }

        private int orderCount() {
            return orderCount;
        }

        private int customerCount() {
            return customerIds.size();
        }
    }
}

package com.zone.agri.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.request.product.RecommendationClickRequest;
import com.zone.agri.dto.response.product.FrequentlyBoughtTogetherResponse;
import com.zone.agri.dto.response.product.ProductResponse;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductRecommendation;
import com.zone.agri.entity.ProductRecommendationClick;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.ProductRecommendationClickRepository;
import com.zone.agri.repository.ProductRecommendationRepository;
import com.zone.agri.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRecommendationService {

    private static final int DEFAULT_LIMIT = 4;
    private static final int MAX_LIMIT = 10;
    private static final int CANDIDATE_FETCH_SIZE = 50;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<FrequentlyBoughtTogetherResponse>> RESPONSE_LIST_TYPE =
            new TypeReference<>() {
            };

    private static final int PERSONALIZED_DEFAULT_LIMIT = 8;
    private static final int PERSONALIZED_MAX_LIMIT = 12;
    private static final int PERSONALIZED_CANDIDATE_FETCH_SIZE = 20;
    private static final TypeReference<List<ProductResponse>> PRODUCT_RESPONSE_LIST_TYPE =
            new TypeReference<>() {
            };

    private final ProductRecommendationRepository productRecommendationRepository;
    private final ProductRecommendationClickRepository productRecommendationClickRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<FrequentlyBoughtTogetherResponse> getFrequentlyBoughtTogether(Long productId, Integer requestedLimit) {
        if (productId == null) {
            return List.of();
        }

        int limit = normalizeLimit(requestedLimit);
        String cacheKey = cacheKey(productId, limit);
        List<FrequentlyBoughtTogetherResponse> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<FrequentlyBoughtTogetherResponse> responses = loadFrequentlyBoughtTogether(productId, limit);
        writeCache(cacheKey, responses);
        return responses;
    }

    @Transactional
    public void trackClick(Long productId, RecommendationClickRequest request) {
        if (productId == null || request == null || request.getRecommendedProductId() == null) {
            throw new BadRequestException("Thiếu thông tin sản phẩm được gợi ý.");
        }
        if (productId.equals(request.getRecommendedProductId())) {
            throw new BadRequestException("Sản phẩm không thể tự gợi ý chính nó.");
        }

        productRecommendationClickRepository.save(ProductRecommendationClick.builder()
                .productId(productId)
                .recommendedProductId(request.getRecommendedProductId())
                .source(safeSource(request.getSource()))
                .clickedAt(LocalDateTime.now())
                .build());
    }

    /**
     * Ca nhan hoa theo tai khoan: lay toan bo san pham user da MUA (don COMPLETED), voi moi san pham
     * do tra cuu dung bang product_recommendations (thuat toan market-basket that, khong tinh gi
     * moi) de lay cac san pham hay di kem, gop lai + loai trung + loai san pham da mua roi, sap theo
     * lift/confidence cao nhat. Guest (userId null) hoac user chua tung mua gi tra ve rong.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getPersonalizedRecommendations(Long userId, Integer requestedLimit) {
        if (userId == null) {
            return List.of();
        }

        int limit = normalizePersonalizedLimit(requestedLimit);
        String cacheKey = personalizedCacheKey(userId, limit);
        List<ProductResponse> cached = readProductResponseCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<ProductResponse> responses = loadPersonalizedRecommendations(userId, limit);
        writeProductResponseCache(cacheKey, responses);
        return responses;
    }

    private List<ProductResponse> loadPersonalizedRecommendations(Long userId, int limit) {
        List<Long> purchasedIds = orderRepository.findDistinctPurchasedProductIdsByUserId(userId);
        if (purchasedIds.isEmpty()) {
            return List.of();
        }
        Set<Long> purchasedSet = new HashSet<>(purchasedIds);

        Map<Long, ProductRecommendation> bestByCandidate = new LinkedHashMap<>();
        for (Long productId : purchasedIds) {
            List<ProductRecommendation> candidates = productRecommendationRepository
                    .findByProductIdOrderByLiftDescConfidenceDesc(productId, PageRequest.of(0, PERSONALIZED_CANDIDATE_FETCH_SIZE));
            for (ProductRecommendation candidate : candidates) {
                Long recommendedId = candidate.getRecommendedProductId();
                if (recommendedId == null || purchasedSet.contains(recommendedId)) {
                    continue;
                }
                ProductRecommendation existing = bestByCandidate.get(recommendedId);
                if (existing == null || candidate.getLift().compareTo(existing.getLift()) > 0) {
                    bestByCandidate.put(recommendedId, candidate);
                }
            }
        }
        if (bestByCandidate.isEmpty()) {
            return List.of();
        }

        List<Long> recommendedIds = bestByCandidate.values().stream()
                .sorted(Comparator.comparing(ProductRecommendation::getLift).reversed()
                        .thenComparing(ProductRecommendation::getConfidence, Comparator.reverseOrder()))
                .map(ProductRecommendation::getRecommendedProductId)
                .toList();

        Map<Long, Product> productMap = productRepository.findPublicByIds(recommendedIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product, (left, right) -> left, LinkedHashMap::new));
        Set<Long> inStockProductIds = loadInStockProductIds(recommendedIds);

        return recommendedIds.stream()
                .map(productMap::get)
                .filter(product -> isPublicVisible(product) && inStockProductIds.contains(product.getId()))
                .limit(limit)
                .map(productService::convertToResponse)
                .toList();
    }

    private int normalizePersonalizedLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return PERSONALIZED_DEFAULT_LIMIT;
        }
        if (requestedLimit < 1 || requestedLimit > PERSONALIZED_MAX_LIMIT) {
            throw new BadRequestException("limit phải nằm trong khoảng 1 đến " + PERSONALIZED_MAX_LIMIT + ".");
        }
        return requestedLimit;
    }

    private String personalizedCacheKey(Long userId, int limit) {
        return "product:recommend-for-user:" + userId + ":" + limit;
    }

    private List<ProductResponse> readProductResponseCache(String cacheKey) {
        try {
            String raw = redisTemplate.opsForValue().get(cacheKey);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, PRODUCT_RESPONSE_LIST_TYPE);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Skipped reading personalized recommendation cache {}: {}", cacheKey, ex.getMessage());
            return null;
        }
    }

    private void writeProductResponseCache(String cacheKey, List<ProductResponse> responses) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(responses),
                    CACHE_TTL.toMinutes(),
                    TimeUnit.MINUTES
            );
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Skipped writing personalized recommendation cache {}: {}", cacheKey, ex.getMessage());
        }
    }

    private List<FrequentlyBoughtTogetherResponse> loadFrequentlyBoughtTogether(Long productId, int limit) {
        List<ProductRecommendation> candidates = productRecommendationRepository
                .findByProductIdOrderByLiftDescConfidenceDesc(productId, PageRequest.of(0, CANDIDATE_FETCH_SIZE));
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> recommendedIds = candidates.stream()
                .map(ProductRecommendation::getRecommendedProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (recommendedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productMap = productRepository.findPublicByIds(recommendedIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product, (left, right) -> left, LinkedHashMap::new));
        Set<Long> inStockProductIds = loadInStockProductIds(recommendedIds);

        return candidates.stream()
                .map(recommendation -> toResponse(recommendation, productMap, inStockProductIds))
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();
    }

    private FrequentlyBoughtTogetherResponse toResponse(
            ProductRecommendation recommendation,
            Map<Long, Product> productMap,
            Set<Long> inStockProductIds
    ) {
        Product product = productMap.get(recommendation.getRecommendedProductId());
        if (!isPublicVisible(product) || !inStockProductIds.contains(product.getId())) {
            return null;
        }

        ProductResponse productResponse = productService.convertToResponse(product);
        return FrequentlyBoughtTogetherResponse.builder()
                .product(productResponse)
                .supportCount(recommendation.getSupportCount())
                .customerCount(recommendation.getCustomerCount())
                .support(recommendation.getSupport())
                .confidence(recommendation.getConfidence())
                .lift(recommendation.getLift())
                .calculatedAt(recommendation.getCalculatedAt())
                .build();
    }

    private Set<Long> loadInStockProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptySet();
        }
        return inventoryRepository.sumActiveBranchQuantityGroupByProductIds(productIds).stream()
                .filter(row -> row.length >= 2 && row[0] != null && row[1] != null)
                .filter(row -> ((Number) row[1]).longValue() > 0)
                .map(row -> ((Number) row[0]).longValue())
                .collect(Collectors.toSet());
    }

    private boolean isPublicVisible(Product product) {
        return product != null
                && product.getStatus() == ProductStatus.ACTIVE
                && product.getCategory() != null
                && product.getCategory().getStatus() == CategoryStatus.ACTIVE;
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (requestedLimit < 1 || requestedLimit > MAX_LIMIT) {
            throw new BadRequestException("limit phải nằm trong khoảng 1 đến " + MAX_LIMIT + ".");
        }
        return requestedLimit;
    }

    private String safeSource(String source) {
        if (source == null || source.isBlank()) {
            return "product_detail";
        }
        String trimmed = source.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }

    private String cacheKey(Long productId, int limit) {
        return "product:frequently-bought-together:" + productId + ":" + limit;
    }

    private List<FrequentlyBoughtTogetherResponse> readCache(String cacheKey) {
        try {
            String raw = redisTemplate.opsForValue().get(cacheKey);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, RESPONSE_LIST_TYPE);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Skipped reading product recommendation cache {}: {}", cacheKey, ex.getMessage());
            return null;
        }
    }

    private void writeCache(String cacheKey, List<FrequentlyBoughtTogetherResponse> responses) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(responses),
                    CACHE_TTL.toMinutes(),
                    TimeUnit.MINUTES
            );
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Skipped writing product recommendation cache {}: {}", cacheKey, ex.getMessage());
        }
    }
}

package com.zone.agri.service.miniapp;

import com.zone.agri.dto.miniapp.ai.AiStockItem;
import com.zone.agri.dto.miniapp.response.SuggestedProductResponse;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductImage;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase BE-3: Nâng cấp product suggestion cho Mini App.
 *
 * Trách nhiệm:
 * 1. Chọn và xếp hạng candidate products theo disease_code trước khi gửi AI
 * 2. Clamp + dedup AI output (related_product_ids)
 * 3. Resolve price an toàn từ inventory
 * 4. Build SuggestedProductResponse sạch cho FE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MiniAppProductSuggestionService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final MiniAppDiseaseProductMapping diseaseMapping;

    @Value("${app.web-base-url:https://agrishrimp.io.vn}")
    private String webBaseUrl;

    /** Số sản phẩm tối đa gửi sang AI — kiểm soát prompt noise */
    private static final int MAX_CANDIDATE_PRODUCTS = 20;

    // =========================================================
    // 1. CANDIDATE PRODUCTS — chọn lọc + xếp hạng
    // =========================================================

    /**
     * Phase BE-3: Lấy candidate products đã được xếp hạng theo disease_code.
     *
     * Pipeline:
     *  DB → tất cả ACTIVE (kèm images, category) → rank → limit(10) → return
     *
     * @Transactional để category lazy-load được giải quyết trong session,
     * sau đó category proxy đã initialized nên safe khi dùng ngoài transaction.
     */
    @Transactional(readOnly = true)
    public List<Product> getCandidateProductsForDisease(String diseaseCode) {
        List<Product> all = productRepository.findActiveProductsForMiniApp(ProductStatus.ACTIVE);
        List<Product> ranked = rankCandidateProducts(diseaseCode, all);
        List<Product> candidates = ranked.stream()
                .limit(MAX_CANDIDATE_PRODUCTS)
                .collect(Collectors.toList());
        log.info("[MiniApp] candidateProducts diseaseCode={}, pool={}, selected={}",
                diseaseCode, all.size(), candidates.size());
        return candidates;
    }

    /**
     * Xếp hạng sản phẩm theo mức độ phù hợp với disease_code.
     * Score cao hơn = ưu tiên hơn.
     *
     * Tiêu chí (xem scoreProduct):
     *  +3  category khớp disease keyword
     *  +2  có ảnh sản phẩm
     */
    public List<Product> rankCandidateProducts(String diseaseCode, List<Product> products) {
        return products.stream()
                .map(p -> new AbstractMap.SimpleEntry<>(p, scoreProduct(diseaseCode, p)))
                .sorted(Map.Entry.<Product, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // =========================================================
    // 2. AI PAYLOAD — chuyển Product → AiStockItem
    // =========================================================

    /**
     * Chuyển candidate products → AiStockItem để gửi sang AI generate-prescription.
     * Bao gồm tên danh mục để AI có context phân loại thuốc/chế phẩm tốt hơn.
     */
    public List<AiStockItem> toAiStockItems(List<Product> products) {
        return products.stream()
                .map(p -> AiStockItem.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .category(p.getCategory() != null ? p.getCategory().getName() : null)
                        .dosage(null)
                        .quantity(null)
                        .notes(null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Build map id → Product để clamp AI output trong O(1).
     */
    public Map<Long, Product> toProductMap(List<Product> products) {
        return products.stream().collect(Collectors.toMap(Product::getId, p -> p));
    }

    // =========================================================
    // 3. PRICE — batch-resolve từ inventory
    // =========================================================

    /**
     * Batch-lấy giá bán = MIN(importPrice lô còn hàng) × 1.3.
     * Dùng cùng công thức giá với PublicProductService để nhất quán.
     *
     * Sản phẩm không có lô hàng → không có entry trong map → resolveProductPrice fallback về 0L.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> getPriceMap(List<Product> products) {
        if (products.isEmpty()) return Collections.emptyMap();
        List<Long> ids = products.stream().map(Product::getId).collect(Collectors.toList());
        List<Object[]> rows = inventoryRepository.findMinImportPriceGroupByProductIds(ids);
        return rows.stream().collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> {
                    BigDecimal importPrice = (BigDecimal) row[1];
                    return importPrice != null
                            ? importPrice.multiply(BigDecimal.valueOf(1.3)).longValue()
                            : 0L;
                }
        ));
    }

    /**
     * Lấy giá bán an toàn cho một sản phẩm.
     * Fallback về 0L nếu chưa có lô nhập → FE hiển thị "Liên hệ".
     */
    public long resolveProductPrice(Product product, Map<Long, Long> priceMap) {
        return priceMap.getOrDefault(product.getId(), 0L);
    }

    // =========================================================
    // 4. CLAMP + MAP — AI related_product_ids → FE response
    // =========================================================

    /**
     * Map related_product_ids từ AI → danh sách SuggestedProductResponse cho FE.
     *
     * Clamp:   chỉ cho phép id nằm trong productMap (tập BE đã gửi sang AI).
     *          Id lạ / hallucination từ AI bị loại bỏ hoàn toàn.
     * Dedup:   distinct() loại id trùng, giữ nguyên thứ tự AI trả về.
     * Safe:    null list / null id / id không tồn tại → bỏ qua, không crash.
     */
    public List<SuggestedProductResponse> mapRelatedProductIdsToSuggestedProducts(
            List<Long> relatedIds,
            Map<Long, Product> productMap,
            Map<Long, Long> priceMap) {

        if (relatedIds == null || relatedIds.isEmpty()) {
            return Collections.emptyList();
        }
        return relatedIds.stream()
                .filter(Objects::nonNull)           // guard: null id từ AI
                .distinct()                          // dedup, preserve order
                .map(productMap::get)               // clamp: id ngoài tập → null
                .filter(Objects::nonNull)           // drop unmapped
                .map(p -> toSuggestedProductResponse(p, priceMap))
                .collect(Collectors.toList());
    }

    // =========================================================
    // PRIVATE
    // =========================================================

    private int scoreProduct(String diseaseCode, Product p) {
        int score = 0;
        // +3: category tên khớp disease keyword
        if (p.getCategory() != null
                && diseaseMapping.categoryMatchesDisease(p.getCategory().getName(), diseaseCode)) {
            score += 3;
        }
        // +2: có ảnh → FE render được
        if (p.getProductImages() != null && !p.getProductImages().isEmpty()) {
            score += 2;
        }
        return score;
    }

    private SuggestedProductResponse toSuggestedProductResponse(Product p, Map<Long, Long> priceMap) {
        String imageUrl = (p.getProductImages() != null)
                ? p.getProductImages().stream()
                        .findFirst()
                        .map(ProductImage::getImageUrl)
                        .orElse(null)
                : null;

        String webUrl = (p.getSlug() != null && !p.getSlug().isBlank())
                ? webBaseUrl + "/san-pham/" + p.getSlug()
                : null;

        return SuggestedProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .image(imageUrl)
                .price(resolveProductPrice(p, priceMap))
                .webUrl(webUrl)
                .build();
    }
}

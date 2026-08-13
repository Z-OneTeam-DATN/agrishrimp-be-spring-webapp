package com.zone.agri.service;

import com.zone.agri.dto.response.product.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.entity.enums.VariantStatus;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service phục vụ API công khai (website bán hàng).
 *
 * Nguyên tắc bảo mật dữ liệu:
 * - Không trả costPrice / importPrice
 * - Không trả số lượng tồn kho chi tiết / theo chi nhánh / số lô
 * - Không trả warehouseId / branchId
 * - Chỉ trả sản phẩm ACTIVE, thuộc danh mục ACTIVE, với variant ACTIVE
 * - GIÁ BÁN: Lấy từ lô hàng cũ nhất còn tồn (FIFO) * 1.3 (Lợi nhuận 30%)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PublicSellingPriceService publicSellingPriceService;

    // =========================================================================
    // LIST — phân trang, tìm kiếm, lọc theo danh mục
    // =========================================================================

    public Page<PublicProductResponse> getPublicProducts(
            String keyword,
            Long categoryId,
            Long brandId,
            Pageable pageable) {

        Page<Long> idPage = productRepository.findPublicProductIds(
                blankToNull(keyword), categoryId, brandId, pageable);

        if (idPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> ids = idPage.getContent();

        List<Product> products = productRepository.findPublicByIds(ids);

        Map<Long, Long> stockMap = buildStockMap(ids);
        Map<Long, Long> soldCountMap = buildSoldCountMap(ids);

        Map<Long, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<PublicProductResponse> responses = ids.stream()
                .map(id -> toPublicResponse(productById.get(id), stockMap, soldCountMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, idPage.getTotalElements());
    }

    // =========================================================================
    // DETAIL — by ID
    // =========================================================================

    public PublicProductResponse getPublicProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại."));
        assertPublicVisible(product);
        return toPublicResponse(
                product,
                buildStockMap(List.of(product.getId())),
                buildSoldCountMap(List.of(product.getId()))
        );
    }

    // =========================================================================
    // DETAIL — by slug (SEO-friendly URL)
    // =========================================================================

    public PublicProductResponse getPublicProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại."));
        assertPublicVisible(product);
        return toPublicResponse(
                product,
                buildStockMap(List.of(product.getId())),
                buildSoldCountMap(List.of(product.getId()))
        );
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private void assertPublicVisible(Product product) {
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new NotFoundException("Sản phẩm không tồn tại.");
        }
        if (product.getCategory() == null
                || product.getCategory().getStatus() != CategoryStatus.ACTIVE) {
            throw new NotFoundException("Sản phẩm không tồn tại.");
        }
    }

    private Map<Long, Long> buildStockMap(List<Long> productIds) {
        if (productIds.isEmpty()) return Collections.emptyMap();
        return inventoryRepository.sumQuantityGroupByProductIds(productIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    private Map<Long, Long> buildSoldCountMap(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> soldCountMap = new LinkedHashMap<>();

        productRepository.sumLegacySoldQuantityByProductIds(productIds)
                .forEach(row -> soldCountMap.merge(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        Long::sum
                ));

        productRepository.sumSubOrderSoldQuantityByProductIds(productIds)
                .forEach(row -> soldCountMap.merge(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        Long::sum
                ));

        return soldCountMap;
    }

    private PublicProductResponse toPublicResponse(
            Product product,
            Map<Long, Long> stockMap,
            Map<Long, Long> soldCountMap
    ) {
        if (product == null) return null;

        boolean isOutOfStock = stockMap.getOrDefault(product.getId(), 0L) <= 0;

        List<String> imageUrls = product.getProductImages() != null
                ? product.getProductImages().stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList())
                : Collections.emptyList();

        List<PublicVariantResponse> variants = product.getVariants() != null
                ? product.getVariants().stream()
                .filter(v -> v.getStatus() == VariantStatus.ACTIVE)
                .map(this::toPublicVariant) // Chuyển map xuống hàm dưới để xử lý giá
                .collect(Collectors.toList())
                : Collections.emptyList();

        PublicProductResponse.CategoryInfo categoryInfo = null;
        if (product.getCategory() != null) {
            categoryInfo = PublicProductResponse.CategoryInfo.builder()
                    .id(product.getCategory().getId())
                    .name(product.getCategory().getName())
                    .build();
        }

        return PublicProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDesc(product.getShortDesc())
                .description(product.getDescription())
                .baseSku(product.getBaseSku())
                .soldCount(soldCountMap.getOrDefault(product.getId(), 0L))
                .ratingAverage(product.getRatingAverage())
                .reviewCount(product.getReviewCount())
                .category(categoryInfo)
                .supplierName(product.getBrand() != null ? product.getBrand().getName() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .imageUrls(imageUrls)
                .variants(variants)
                .isOutOfStock(isOutOfStock)
                .build();
    }

    /**
     * Chuyển ProductVariant → PublicVariantResponse.
     * Tự động lấy giá vốn lô cũ nhất * 1.3 làm giá bán.
     */
    private PublicVariantResponse toPublicVariant(ProductVariant variant) {

        BigDecimal currentSellingPrice = publicSellingPriceService.resolveDisplayedVariantPrice(variant);

        // Các thuộc tính giữ nguyên
        List<AttributeValueResponse> attrs = variant.getAttributeValues() != null
                ? variant.getAttributeValues().stream()
                .map(sav -> AttributeValueResponse.builder()
                        .attributeId(sav.getAttribute().getId())
                        .attributeName(sav.getAttribute().getName())
                        .attributeCode(sav.getAttribute().getCode())
                        .valueId(sav.getAttributeValue().getId())
                        .value(sav.getAttributeValue().getValue())
                        .build())
                .collect(Collectors.toList())
                : Collections.emptyList();

        return PublicVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .price(currentSellingPrice) // 👉 Truyền giá bán động vào đây
                .wholesalePrice(null) // Đã ẩn giá sỉ ra public theo chuẩn mới
                .shippingWeight(variant.getShippingWeight())
                .unit("Cái") // Giá trị mặc định do entity không còn lưu unit
                .imageUrl(variant.getImageUrl())
                .attributeValues(attrs)
                .build();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

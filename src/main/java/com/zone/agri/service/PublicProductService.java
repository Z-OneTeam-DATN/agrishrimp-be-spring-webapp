package com.zone.agri.service;

import com.zone.agri.dto.product.*;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service phục vụ API công khai (website bán hàng).
 *
 * Nguyên tắc bảo mật dữ liệu:
 *  - Không trả costPrice / importPrice
 *  - Không trả số lượng tồn kho chi tiết / theo chi nhánh
 *  - Không trả warehouseId / branchId
 *  - Chỉ trả sản phẩm ACTIVE, thuộc danh mục ACTIVE, với variant ACTIVE
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    // =========================================================================
    // LIST — phân trang, tìm kiếm, lọc theo danh mục
    // =========================================================================

    public Page<PublicProductResponse> getPublicProducts(
            String keyword,
            Long categoryId,
            Long brandId,
            Pageable pageable) {

        // Bước 1: lấy page IDs (đúng pagination, tránh Hibernate in-memory warning)
        Page<Long> idPage = productRepository.findPublicProductIds(
                blankToNull(keyword), categoryId, brandId, pageable);

        if (idPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> ids = idPage.getContent();

        // Bước 2: fetch đầy đủ dữ liệu cho các IDs
        List<Product> products = productRepository.findPublicByIds(ids);

        // Bước 3: tổng tồn kho theo batch (1 query cho tất cả sản phẩm trên trang)
        Map<Long, Long> stockMap = buildStockMap(ids);

        // Bước 4: map → response, giữ đúng thứ tự của idPage
        Map<Long, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<PublicProductResponse> responses = ids.stream()
                .map(id -> toPublicResponse(productById.get(id), stockMap))
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
        return toPublicResponse(product, buildStockMap(List.of(product.getId())));
    }

    // =========================================================================
    // DETAIL — by slug (SEO-friendly URL)
    // =========================================================================

    public PublicProductResponse getPublicProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại."));
        assertPublicVisible(product);
        return toPublicResponse(product, buildStockMap(List.of(product.getId())));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /** Ném NotFoundException (404) nếu sản phẩm không đủ điều kiện hiển thị công khai */
    private void assertPublicVisible(Product product) {
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new NotFoundException("Sản phẩm không tồn tại.");
        }
        if (product.getCategory() == null
                || product.getCategory().getStatus() != CategoryStatus.ACTIVE) {
            throw new NotFoundException("Sản phẩm không tồn tại.");
        }
    }

    /**
     * Lấy tổng tồn kho (toàn hệ thống) cho nhiều sản phẩm trong 1 query.
     * Trả về Map<productId, totalStock>.
     * Sản phẩm không có record Inventory sẽ không có entry → getOrDefault 0.
     */
    private Map<Long, Long> buildStockMap(List<Long> productIds) {
        if (productIds.isEmpty()) return Collections.emptyMap();
        return inventoryRepository.sumQuantityGroupByProductIds(productIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    /** Chuyển Product entity → PublicProductResponse (safe, không lộ nội bộ) */
    private PublicProductResponse toPublicResponse(Product product, Map<Long, Long> stockMap) {
        if (product == null) return null;

        boolean isOutOfStock = stockMap.getOrDefault(product.getId(), 0L) <= 0;

        List<String> imageUrls = product.getProductImages() != null
                ? product.getProductImages().stream()
                        .map(ProductImage::getImageUrl)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        // Chỉ trả variant ACTIVE — filter tại đây, không phụ thuộc DB query
        List<PublicVariantResponse> variants = product.getVariants() != null
                ? product.getVariants().stream()
                        .filter(v -> v.getStatus() == VariantStatus.ACTIVE)
                        .map(this::toPublicVariant)
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
                .origin(product.getOrigin())
                .baseSku(product.getBaseSku())
                .category(categoryInfo)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .imageUrls(imageUrls)
                .variants(variants)
                .isOutOfStock(isOutOfStock)
                .build();
    }

    /**
     * Chuyển ProductVariant → PublicVariantResponse.
     * KHÔNG chứa: costPrice, importPrice, quantity, status, imagePublicId.
     */
    private PublicVariantResponse toPublicVariant(ProductVariant variant) {
        List<UnitConversionResponse> conversions = variant.getUnitConversions() != null
                ? variant.getUnitConversions().stream()
                        .map(uc -> UnitConversionResponse.builder()
                                .id(uc.getId())
                                .fromUnit(uc.getFromUnit())
                                .toUnit(uc.getToUnit())
                                .rate(uc.getRate())
                                .build())
                        .collect(Collectors.toList())
                : Collections.emptyList();

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

        // unit: lấy fromUnit của conversion đầu tiên nếu có, vì entity không có cột riêng
        String unit = conversions.isEmpty() ? null : conversions.get(0).getFromUnit();

        return PublicVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .price(variant.getPrice())
                .wholesalePrice(variant.getWholesalePrice())
                .shippingWeight(variant.getShippingWeight())
                .unit(unit)
                .imageUrl(variant.getImageUrl())
                .attributeValues(attrs)
                .unitConversions(conversions)
                .build();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

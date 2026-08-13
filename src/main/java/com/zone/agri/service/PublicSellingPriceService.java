package com.zone.agri.service;

import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicSellingPriceService {

    private static final BigDecimal PUBLIC_DISPLAY_MULTIPLIER = new BigDecimal("1.3");

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public BigDecimal resolveDisplayedVariantPrice(ProductVariant variant) {
        if (variant == null || variant.getId() == null) {
            return BigDecimal.ZERO;
        }

        return resolveDisplayedVariantPrice(variant.getId());
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveDisplayedVariantPrice(Long variantId) {
        if (variantId == null) {
            return BigDecimal.ZERO;
        }

        List<Inventory> batches = inventoryRepository.findByProductVariantId(variantId);
        return batches.stream()
                .filter(inventory -> inventory.getQuantity() != null && inventory.getQuantity() > 0)
                .min(Comparator.comparing(Inventory::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::calculateDisplayedPrice)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calculateDisplayedPrice(Inventory inventory) {
        BigDecimal importPrice = inventory.getImportPrice() != null
                ? inventory.getImportPrice()
                : BigDecimal.ZERO;
        return importPrice.multiply(PUBLIC_DISPLAY_MULTIPLIER);
    }
}

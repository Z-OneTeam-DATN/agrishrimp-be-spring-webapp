package com.zone.agri.service;

import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicSellingPriceService {

    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository variantRepository;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    public BigDecimal resolveDisplayedVariantPrice(ProductVariant variant) {
        if (variant == null || variant.getId() == null) {
            return BigDecimal.ZERO;
        }
        return calculateSellingPriceForVariant(variant);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveDisplayedVariantPrice(Long variantId) {
        if (variantId == null) {
            return BigDecimal.ZERO;
        }

        ProductVariant variant = variantRepository.findById(variantId).orElse(null);
        if (variant == null) {
            return BigDecimal.ZERO;
        }

        return calculateSellingPriceForVariant(variant);
    }

    private BigDecimal calculateSellingPriceForVariant(ProductVariant variant) {
        List<Inventory> allInventories = inventoryRepository.findByProductVariantId(variant.getId());
        List<Inventory> validBatches = allInventories.stream()
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                .filter(inv -> inv.getBranch() != null && inv.getBranch().getStatus() == BranchStatus.ACTIVE)
                .collect(Collectors.toList());

        if (validBatches.isEmpty()) {
            validBatches = allInventories.stream()
                    .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                    .collect(Collectors.toList());
        }

        if (validBatches.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal averageImportPrice = validBatches.stream()
                .map(inv -> inv.getImportPrice() != null ? inv.getImportPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(validBatches.size()), 4, RoundingMode.HALF_UP);

        Product product = variant.getProduct();
        Long categoryId = (product != null && product.getCategory() != null) ? product.getCategory().getId() : null;

        boolean multiTierPricingEnabled = settingService.isMultiTierPricingEnabled();
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();
        String roundingRule = settingService.getProfitRoundingRuleRaw();

        return (multiTierPricingEnabled && categoryId != null)
                ? settingService.calculateSellingPrice(averageImportPrice, categoryId, null)
                : settingService.calculateSellingPrice(averageImportPrice, profitMultiplier, roundingRule);
    }
}

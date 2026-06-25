package com.zone.agri.service;

import com.zone.agri.dto.response.cart.CartItemResponse;
import com.zone.agri.entity.CartItem;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.SKUAttributeValue;
import com.zone.agri.entity.User;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.CartItemRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.SKUAttributeValueRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepo;
    private final ProductVariantRepository variantRepo;
    private final UserRepository userRepo;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final SKUAttributeValueRepository skuAttributeValueRepo;
    private final SettingService settingService;

    public List<CartItemResponse> getMyCart(Long userId) {
        List<CartItem> items = cartItemRepo.findByUserIdWithDetails(userId);
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> variantIds = items.stream()
                .map(item -> item.getProductVariant().getId())
                .distinct()
                .toList();

        BigDecimal profitMultiplier = settingService.getProfitMultiplier();
        String roundingRule = settingService.getProfitRoundingRuleRaw();

        List<Inventory> allInventories = inventoryRepository.rawFindByProductVariantIdIn(variantIds);
        Map<Long, List<Inventory>> inventoryMap = allInventories.stream()
                .collect(Collectors.groupingBy(inv -> inv.getProductVariant().getId()));
        Map<String, BigDecimal> transferImportPriceCache = new HashMap<>();

        List<SKUAttributeValue> allAttributes = skuAttributeValueRepo.findBySkuIdIn(variantIds);
        Map<Long, List<SKUAttributeValue>> attributeMap = allAttributes.stream()
                .collect(Collectors.groupingBy(sav -> sav.getSku().getId()));

        return items.stream().map(item -> {
            ProductVariant variant = item.getProductVariant();
            Product product = variant.getProduct();

            List<SKUAttributeValue> attributes = attributeMap.getOrDefault(variant.getId(), List.of());
            String variantName = !attributes.isEmpty()
                    ? attributes.stream()
                            .map(sav -> sav.getAttribute().getName() + ": " + sav.getAttributeValue().getValue())
                            .collect(Collectors.joining(", "))
                    : variant.getSku();

            List<Inventory> batches = inventoryMap.getOrDefault(variant.getId(), List.of());

            int totalStock = batches.stream()
                    .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            BigDecimal fifoImportPrice = batches.stream()
                    .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                    .sorted(Comparator.comparing(Inventory::getId, Comparator.nullsLast(Long::compareTo)))
                    .map(inv -> resolveDisplayImportPrice(inv, variant.getId(), transferImportPriceCache))
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            BigDecimal sellingPrice = settingService.calculateSellingPrice(
                    fifoImportPrice,
                    profitMultiplier,
                    roundingRule);

            return CartItemResponse.builder()
                    .id(item.getId())
                    .variantId(variant.getId())
                    .name(product != null ? product.getName() : "Sản phẩm")
                    .variant(variantName)
                    .variantName(variantName)
                    .categoryName(product != null && product.getCategory() != null ? product.getCategory().getName() : "")
                    .brandName(product != null && product.getSupplier() != null ? product.getSupplier().getName() : "")
                    .price(sellingPrice)
                    .quantity(item.getQuantity())
                    .stock(totalStock)
                    .image(variant.getImageUrl())
                    .build();
        }).toList();
    }

    private BigDecimal resolveDisplayImportPrice(Inventory inventory, Long variantId,
                                                 Map<String, BigDecimal> transferImportPriceCache) {
        BigDecimal importPrice = inventory.getImportPrice();
        if (!isTransferBatchWithoutCost(inventory)) {
            return importPrice != null ? importPrice : BigDecimal.ZERO;
        }

        Long branchId = inventory.getBranch() != null ? inventory.getBranch().getId() : null;
        if (branchId == null || variantId == null) {
            return BigDecimal.ZERO;
        }

        String cacheKey = branchId + ":" + variantId;
        return transferImportPriceCache.computeIfAbsent(
                cacheKey,
                ignored -> resolveInboundTransferAverageImportPrice(branchId, variantId));
    }

    private boolean isTransferBatchWithoutCost(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        String batchNumber = inventory.getBatchNumber();
        BigDecimal importPrice = inventory.getImportPrice();
        boolean isTransferBatch = batchNumber != null && batchNumber.toUpperCase(Locale.ROOT).startsWith("TRANSFER");
        boolean missingCost = importPrice == null || BigDecimal.ZERO.compareTo(importPrice) == 0;
        return isTransferBatch && missingCost;
    }

    private BigDecimal resolveInboundTransferAverageImportPrice(Long branchId, Long variantId) {
        Object[] summary = inventoryTransactionRepository.summarizeCompletedInboundTransferCost(branchId, variantId);
        if (summary == null || summary.length < 2 || summary[0] == null || summary[1] == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCost = (BigDecimal) summary[0];
        Number totalQty = (Number) summary[1];
        if (totalQty.longValue() <= 0) {
            return BigDecimal.ZERO;
        }

        return totalCost.divide(BigDecimal.valueOf(totalQty.longValue()), 4, RoundingMode.HALF_UP);
    }

    @Transactional
    public void updateCartQuantity(Long userId, Long variantId, Integer delta) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy User"));
        ProductVariant variant = variantRepo.findById(variantId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy sản phẩm"));

        CartItem cartItem = cartItemRepo.findByUserIdAndProductVariantId(userId, variantId)
                .orElseGet(() -> CartItem.builder()
                        .user(user)
                        .productVariant(variant)
                        .quantity(0)
                        .build());

        int newQuantity = cartItem.getQuantity() + delta;

        if (newQuantity <= 0) {
            if (cartItem.getId() != null) {
                cartItemRepo.delete(cartItem);
            }
            return;
        }

        // Cart no longer blocks by current stock. Shortage is handled later in prepare/confirm order flow.
        cartItem.setQuantity(newQuantity);
        cartItemRepo.save(cartItem);
    }

    @Transactional
    public void removeCartItem(Long userId, Long cartItemId) {
        CartItem item = cartItemRepo.findById(cartItemId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy sản phẩm trong giỏ"));

        if (!item.getUser().getId().equals(userId)) {
            throw new BadRequestException("Không có quyền thực hiện!");
        }

        cartItemRepo.delete(item);
    }
}

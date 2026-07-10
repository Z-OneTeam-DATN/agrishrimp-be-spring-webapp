package com.zone.agri.service;

import com.zone.agri.dto.response.order.CartItemDto;
import com.zone.agri.dto.response.order.OrderItemDto;
import com.zone.agri.dto.response.order.OutOfStockItemDto;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Thuật toán Greedy kết hợp lô hàng (FIFO).
 * Tự động tính giá bán = giá vốn của lô * % lợi nhuận hệ thống
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAllocationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final SettingService settingService;

    public record AllocationResult(
            List<SubOrderDraftDto> subOrders,
            List<OutOfStockItemDto> outOfStockItems) {
    }

    public Map<Long, Map<Long, List<Inventory>>> buildInventoryMatrix(List<Long> branchIds, List<Long> variantIds) {
        List<Inventory> inventories = inventoryRepository.rawFindInventoryMatrix(branchIds, variantIds);

        // Use detached copies so quote simulation never mutates managed JPA entities.
        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        for (Inventory inventory : inventories) {
            Long branchId = inventory.getBranch().getId();
            Long variantId = inventory.getProductVariant().getId();

            matrix.computeIfAbsent(branchId, key -> new HashMap<>())
                    .computeIfAbsent(variantId, key -> new ArrayList<>())
                    .add(copyInventory(inventory));
        }
        return matrix;
    }

    public AllocationResult allocate(
            List<CartItemDto> cart,
            Map<Long, ProductVariant> variantMap,
            List<BranchWithRealDistance> branchesSortedByDist,
            Map<Long, Map<Long, List<Inventory>>> inventoryMatrix) {
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();
        String roundingRule = settingService.getProfitRoundingRuleRaw();

        List<SubOrderDraftDto> subOrders = new ArrayList<>();
        List<OutOfStockItemDto> outOfStockItems = new ArrayList<>();

        if (branchesSortedByDist.isEmpty() || cart.isEmpty()) {
            return new AllocationResult(subOrders, outOfStockItems);
        }

        List<BranchWithRealDistance> sellableBranches = branchesSortedByDist.stream()
                .filter(candidate -> !isWarehouse(candidate.branch()))
                .toList();

        if (sellableBranches.isEmpty()) {
            return new AllocationResult(subOrders, outOfStockItems);
        }

        Set<Long> sellableBranchIds = sellableBranches.stream()
                .map(candidate -> candidate.branch().getId())
                .collect(java.util.stream.Collectors.toSet());

        Map<Long, List<OrderItemDto>> branchItems = new LinkedHashMap<>();
        Map<Long, BigDecimal> transferImportPriceCache = new HashMap<>();
        Map<Long, BranchWithRealDistance> branchLookup = sellableBranches.stream()
                .collect(Collectors.toMap(candidate -> candidate.branch().getId(), candidate -> candidate));

        for (CartItemDto item : cart) {
            Long variantId = item.getProductVariantId();
            int requested = Objects.requireNonNullElse(item.getQuantity(), 0);
            if (requested <= 0) {
                continue;
            }

            ProductVariant variant = variantMap.get(variantId);
            String variantName = (variant != null && variant.getSku() != null) ? variant.getSku() : "Unknown";
            String variantSku = variant != null ? variant.getSku() : "";

            for (BranchWithRealDistance branchWithDistance : sellableBranches) {
                if (requested <= 0) {
                    break;
                }

                Long branchId = branchWithDistance.branch().getId();
                Map<Long, List<Inventory>> branchBatches = inventoryMatrix.getOrDefault(branchId, Collections.emptyMap());
                List<Inventory> batches = branchBatches.getOrDefault(variantId, Collections.emptyList());
                if (batches.isEmpty()) {
                    continue;
                }

                int allocatedForBranch = 0;
                BigDecimal resolvedUnitPrice = BigDecimal.ZERO;
                Iterator<Inventory> batchIterator = batches.iterator();
                while (batchIterator.hasNext() && requested > 0) {
                    Inventory batch = batchIterator.next();
                    int availableInBatch = Objects.requireNonNullElse(batch.getQuantity(), 0);
                    if (availableInBatch <= 0) {
                        continue;
                    }

                    int quantityToTake = Math.min(requested, availableInBatch);
                    BigDecimal importPrice = resolveDisplayImportPrice(batch, variantId, transferImportPriceCache);
                    resolvedUnitPrice = calculateSellingPriceSafe(importPrice, profitMultiplier, roundingRule);

                    allocatedForBranch += quantityToTake;
                    batch.setQuantity(availableInBatch - quantityToTake);
                    requested -= quantityToTake;
                }

                if (allocatedForBranch <= 0) {
                    continue;
                }

                if (resolvedUnitPrice.compareTo(BigDecimal.ZERO) == 0) {
                    resolvedUnitPrice = resolveFallbackUnitPrice(
                            variantId,
                            inventoryMatrix,
                            profitMultiplier,
                            roundingRule,
                            transferImportPriceCache);
                }

                branchItems.computeIfAbsent(branchId, ignored -> new ArrayList<>())
                        .add(OrderItemDto.builder()
                                .productVariantId(variantId)
                                .variantName(variantName)
                                .variantSku(variantSku)
                                .quantity(allocatedForBranch)
                                .allocatedQuantity(allocatedForBranch)
                                .missingQuantity(0)
                                .unitPrice(resolvedUnitPrice)
                                .subtotal(resolvedUnitPrice.multiply(BigDecimal.valueOf(allocatedForBranch)))
                                .build());
            }

            if (requested > 0) {
                outOfStockItems.add(OutOfStockItemDto.builder()
                        .productVariantId(variantId)
                        .variantName(variantName)
                        .variantSku(variantSku)
                        .requestedQty(requested)
                        .availableQty(0)
                        .build());
            }
        }

        for (BranchWithRealDistance branchWithDistance : sellableBranches) {
            Long branchId = branchWithDistance.branch().getId();
            List<OrderItemDto> allocatedItems = branchItems.getOrDefault(branchId, Collections.emptyList());
            if (allocatedItems.isEmpty()) {
                continue;
            }

            BigDecimal subtotal = allocatedItems.stream()
                    .map(item -> Objects.requireNonNullElse(item.getSubtotal(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            subOrders.add(buildSubOrderDraft(branchWithDistance, allocatedItems, subtotal));
        }

        return new AllocationResult(subOrders, outOfStockItems);
    }

    private SubOrderDraftDto buildSubOrderDraft(BranchWithRealDistance branchWithDistance,
                                                List<OrderItemDto> allocatedItems,
                                                BigDecimal subtotal) {
        Branch branch = branchWithDistance.branch();
        return SubOrderDraftDto.builder()
                .branchId(branch.getId())
                .branchName(branch.getName())
                .branchAddress(branch.getAddressDetail())
                .fromDistrictId(branch.getDistrictId())
                .durationMinutes(branchWithDistance.durationMinutes())
                .distanceKm(branchWithDistance.distanceKm())
                .items(allocatedItems)
                .subtotal(subtotal)
                .shippingFee(BigDecimal.ZERO)
                .build();
    }

    private Inventory copyInventory(Inventory inventory) {
        return Inventory.builder()
                .id(inventory.getId())
                .quantity(inventory.getQuantity())
                .defectiveQuantity(inventory.getDefectiveQuantity())
                .batchNumber(inventory.getBatchNumber())
                .importPrice(inventory.getImportPrice())
                .expiryDate(inventory.getExpiryDate())
                .shelfLocation(inventory.getShelfLocation())
                .lastReceiptDate(inventory.getLastReceiptDate())
                .minStock(inventory.getMinStock())
                .lastCheckedAt(inventory.getLastCheckedAt())
                .branch(inventory.getBranch())
                .productVariant(inventory.getProductVariant())
                .build();
    }

    private BigDecimal resolveFallbackUnitPrice(Long variantId, Map<Long, Map<Long, List<Inventory>>> matrix,
                                                BigDecimal multiplier, String roundingRule,
                                                Map<Long, BigDecimal> transferImportPriceCache) {
        for (Map<Long, List<Inventory>> branchMap : matrix.values()) {
            List<Inventory> batches = branchMap.getOrDefault(variantId, Collections.emptyList());
            for (Inventory batch : batches) {
                BigDecimal importPrice = resolveDisplayImportPrice(batch, variantId, transferImportPriceCache);
                if (importPrice.compareTo(BigDecimal.ZERO) > 0) {
                    return calculateSellingPriceSafe(importPrice, multiplier, roundingRule);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveDisplayImportPrice(Inventory inventory, Long variantId,
                                                 Map<Long, BigDecimal> transferImportPriceCache) {
        BigDecimal importPrice = inventory.getImportPrice();
        if (!isTransferBatchWithoutCost(inventory)) {
            return importPrice != null ? importPrice : BigDecimal.ZERO;
        }

        Long branchId = inventory.getBranch() != null ? inventory.getBranch().getId() : null;
        if (branchId == null || variantId == null) {
            return BigDecimal.ZERO;
        }

        return transferImportPriceCache.computeIfAbsent(
                buildTransferImportPriceCacheKey(branchId, variantId),
                ignored -> resolveInboundTransferAverageImportPrice(branchId, variantId));
    }

    private long buildTransferImportPriceCacheKey(Long branchId, Long variantId) {
        long safeBranchId = branchId != null ? branchId : 0L;
        long safeVariantId = variantId != null ? variantId : 0L;
        return (safeBranchId << 32) ^ (safeVariantId & 0xffffffffL);
    }

    private BigDecimal calculateSellingPriceSafe(BigDecimal importPrice, BigDecimal multiplier, String roundingRule) {
        BigDecimal resolvedImportPrice = importPrice != null ? importPrice : BigDecimal.ZERO;
        BigDecimal calculatedPrice = settingService.calculateSellingPrice(resolvedImportPrice, multiplier, roundingRule);
        return calculatedPrice != null ? calculatedPrice : BigDecimal.ZERO;
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

    private boolean isBranchFullyStocked(Long branchId, List<CartItemDto> cart,
                                         Map<Long, Map<Long, List<Inventory>>> matrix) {
        Map<Long, List<Inventory>> branchBatches = matrix.getOrDefault(branchId, Collections.emptyMap());
        for (CartItemDto item : cart) {
            int totalAvailable = branchBatches.getOrDefault(item.getProductVariantId(), Collections.emptyList())
                    .stream()
                    .mapToInt(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0))
                    .sum();
            if (totalAvailable < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    private int calculateAllocatableQuantity(Long branchId, List<CartItemDto> cart,
                                             Map<Long, Map<Long, List<Inventory>>> matrix) {
        Map<Long, List<Inventory>> branchBatches = matrix.getOrDefault(branchId, Collections.emptyMap());
        int totalAllocatable = 0;
        for (CartItemDto item : cart) {
            int totalAvailable = branchBatches.getOrDefault(item.getProductVariantId(), Collections.emptyList())
                    .stream()
                    .mapToInt(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0))
                    .sum();
            totalAllocatable += Math.min(totalAvailable, Objects.requireNonNullElse(item.getQuantity(), 0));
        }
        return totalAllocatable;
    }

    private boolean hasAllocatableStock(Long branchId, List<CartItemDto> cart,
                                        Map<Long, Map<Long, List<Inventory>>> matrix) {
        return calculateAllocatableQuantity(branchId, cart, matrix) > 0;
    }

    private boolean isWarehouse(com.zone.agri.entity.Branch branch) {
        return branch != null
                && branch.getBranchType() != null
                && "WAREHOUSE".equalsIgnoreCase(branch.getBranchType());
    }

    private int calculateTotalAvailable(Long variantId,
                                        Map<Long, Map<Long, List<Inventory>>> matrix,
                                        Set<Long> allowedBranchIds) {
        return matrix.entrySet().stream()
                .filter(entry -> allowedBranchIds.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().getOrDefault(variantId, Collections.emptyList()).stream())
                .mapToInt(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0))
                .sum();
    }
}

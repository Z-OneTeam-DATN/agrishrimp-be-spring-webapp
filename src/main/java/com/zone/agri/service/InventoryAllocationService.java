package com.zone.agri.service;

import com.zone.agri.dto.response.order.CartItemDto;
import com.zone.agri.dto.response.order.OrderItemDto;
import com.zone.agri.dto.response.order.OutOfStockItemDto;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.exception.BadRequestException;
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

        List<BranchWithRealDistance> preferredBranches = branchesSortedByDist.stream()
                .filter(candidate -> !isWarehouse(candidate.branch()))
                .toList();

        List<BranchWithRealDistance> sellableBranches = !preferredBranches.isEmpty()
                ? preferredBranches
                : branchesSortedByDist.stream()
                        .filter(candidate -> candidate.branch() != null)
                        .toList();

        if (sellableBranches.isEmpty()) {
            return new AllocationResult(subOrders, outOfStockItems);
        }

        Set<Long> sellableBranchIds = sellableBranches.stream()
                .map(candidate -> candidate.branch().getId())
                .collect(java.util.stream.Collectors.toSet());

        BranchWithRealDistance selectedBranchWithDistance = sellableBranches.stream()
                .filter(candidate -> isBranchFullyStocked(candidate.branch().getId(), cart, inventoryMatrix))
                .findFirst()
                .orElse(sellableBranches.get(0));

        Long selectedBranchId = selectedBranchWithDistance.branch().getId();
        Map<Long, List<Inventory>> branchBatches = inventoryMatrix.getOrDefault(selectedBranchId, Collections.emptyMap());
        Map<Long, BigDecimal> transferImportPriceCache = new HashMap<>();

        List<OrderItemDto> allocatedItems = new ArrayList<>();

        for (CartItemDto item : cart) {
            Long variantId = item.getProductVariantId();
            int requested = item.getQuantity();
            int originalRequested = requested;
            int totalAvailableAcrossBranches = calculateTotalAvailable(variantId, inventoryMatrix, sellableBranchIds);

            List<Inventory> batches = branchBatches.getOrDefault(variantId, new ArrayList<>());
            ProductVariant variant = variantMap.get(variantId);
            String variantName = (variant != null && variant.getSku() != null) ? variant.getSku() : "Unknown";
            String variantSku = variant != null ? variant.getSku() : "";
            Long categoryId = (variant != null && variant.getProduct() != null && variant.getProduct().getCategory() != null)
                    ? variant.getProduct().getCategory().getId()
                    : null;

            int totalAllocatedForItem = 0;
            BigDecimal lastUnitPrice = BigDecimal.ZERO;
            BigDecimal allocatedSubtotal = BigDecimal.ZERO;

            Iterator<Inventory> batchIterator = batches.iterator();
            while (batchIterator.hasNext() && requested > 0) {
                Inventory batch = batchIterator.next();
                int availableInBatch = availableForSale(batch);
                if (availableInBatch <= 0) {
                    continue;
                }

                int quantityToTake = Math.min(requested, availableInBatch);
                BigDecimal importPrice = resolveDisplayImportPrice(batch, variantId, transferImportPriceCache);
                BigDecimal batchUnitPrice = settingService.calculateSellingPrice(importPrice, categoryId, batch.getExpiryDate());
                lastUnitPrice = batchUnitPrice;

                allocatedSubtotal = allocatedSubtotal.add(batchUnitPrice.multiply(BigDecimal.valueOf(quantityToTake)));
                totalAllocatedForItem += quantityToTake;
                batch.setQuantity(availableInBatch - quantityToTake);
                requested -= quantityToTake;
            }

            if (lastUnitPrice.compareTo(BigDecimal.ZERO) == 0) {
                lastUnitPrice = resolveFallbackUnitPrice(
                        variantId,
                        inventoryMatrix,
                        categoryId,
                        transferImportPriceCache);
            }

            if (lastUnitPrice.compareTo(BigDecimal.ZERO) <= 0 && totalAvailableAcrossBranches > 0) {
                throw new BadRequestException("San pham " + variantName
                        + " chua co gia ban hop le. Vui long kiem tra gia nhap ton kho truoc khi dat hang.");
            }

            BigDecimal missingSubtotal = lastUnitPrice.multiply(BigDecimal.valueOf(requested));
            BigDecimal itemSubtotal = allocatedSubtotal.add(missingSubtotal);
            BigDecimal effectiveUnitPrice = totalAllocatedForItem > 0
                    ? allocatedSubtotal.divide(BigDecimal.valueOf(totalAllocatedForItem), 2, RoundingMode.HALF_UP)
                    : lastUnitPrice;

            allocatedItems.add(OrderItemDto.builder()
                    .productVariantId(variantId)
                    .variantName(variantName)
                    .variantSku(variantSku)
                    .quantity(originalRequested)
                    .allocatedQuantity(totalAllocatedForItem)
                    .missingQuantity(requested)
                    .unitPrice(effectiveUnitPrice)
                    .subtotal(itemSubtotal)
                    .build());

            int networkShortage = Math.max(0, originalRequested - totalAvailableAcrossBranches);
            if (networkShortage > 0) {
                outOfStockItems.add(OutOfStockItemDto.builder()
                        .productVariantId(variantId)
                        .variantName(variantName)
                        .variantSku(variantSku)
                        .requestedQty(networkShortage)
                        .availableQty(0)
                        .build());
            }

        }

        if (!allocatedItems.isEmpty()) {
            BigDecimal subtotal = allocatedItems.stream()
                    .map(OrderItemDto::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            subOrders.add(SubOrderDraftDto.builder()
                    .branchId(selectedBranchId)
                    .branchName(selectedBranchWithDistance.branch().getName())
                    .branchAddress(selectedBranchWithDistance.branch().getAddressDetail())
                    .fromDistrictId(null)
                    .durationMinutes(selectedBranchWithDistance.durationMinutes())
                    .distanceKm(selectedBranchWithDistance.distanceKm())
                    .items(allocatedItems)
                    .subtotal(subtotal)
                    .shippingFee(BigDecimal.ZERO)
                    .build());
        }

        return new AllocationResult(subOrders, outOfStockItems);
    }

    private Inventory copyInventory(Inventory inventory) {
        return Inventory.builder()
                .id(inventory.getId())
                .quantity(inventory.getQuantity())
                .defectiveQuantity(inventory.getDefectiveQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
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
                                                Long categoryId,
                                                Map<Long, BigDecimal> transferImportPriceCache) {
        for (Map<Long, List<Inventory>> branchMap : matrix.values()) {
            List<Inventory> batches = branchMap.getOrDefault(variantId, Collections.emptyList());
            for (Inventory batch : batches) {
                BigDecimal importPrice = resolveDisplayImportPrice(batch, variantId, transferImportPriceCache);
                if (importPrice.compareTo(BigDecimal.ZERO) > 0) {
                    return settingService.calculateSellingPrice(importPrice, categoryId, batch.getExpiryDate());
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
                branchId,
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

    private boolean isBranchFullyStocked(Long branchId, List<CartItemDto> cart,
                                         Map<Long, Map<Long, List<Inventory>>> matrix) {
        Map<Long, List<Inventory>> branchBatches = matrix.getOrDefault(branchId, Collections.emptyMap());
        for (CartItemDto item : cart) {
            int totalAvailable = branchBatches.getOrDefault(item.getProductVariantId(), Collections.emptyList())
                    .stream()
                    .mapToInt(this::availableForSale)
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
                    .mapToInt(this::availableForSale)
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
                .mapToInt(this::availableForSale)
                .sum();
    }

    private int availableForSale(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }

        int quantity = Objects.requireNonNullElse(inventory.getQuantity(), 0);
        int reserved = Objects.requireNonNullElse(inventory.getReservedQuantity(), 0);
        return Math.max(0, quantity - reserved);
    }
}


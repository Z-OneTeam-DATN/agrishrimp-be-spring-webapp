package com.zone.agri.service;

import com.zone.agri.dto.response.order.CartItemDto;
import com.zone.agri.dto.response.order.OrderItemDto;
import com.zone.agri.dto.response.order.OutOfStockItemDto;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.dto.response.order.SuggestedTransferDto;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.enums.VietnamRegion;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAllocationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final SettingService settingService;
    private final PublicSellingPriceService publicSellingPriceService;
    private final VietnamRegionResolver vietnamRegionResolver;

    public record AllocationResult(
            List<SubOrderDraftDto> subOrders,
            List<OutOfStockItemDto> outOfStockItems,
            List<SuggestedTransferDto> suggestedTransfers,
            String customerRegion,
            String servingBranchRegion,
            String adjacentRegionUsed,
            String distanceSource) {
    }

    private record ServingBranchSelection(
            BranchWithRealDistance servingBranch,
            VietnamRegion servingRegion,
            VietnamRegion adjacentRegionUsed,
            List<BranchWithRealDistance> transferSources,
            Set<Long> accessibleBranchIds) {
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
        return allocate(cart, variantMap, branchesSortedByDist, inventoryMatrix, null);
    }

    public AllocationResult allocate(
            List<CartItemDto> cart,
            Map<Long, ProductVariant> variantMap,
            List<BranchWithRealDistance> branchesSortedByDist,
            Map<Long, Map<Long, List<Inventory>>> inventoryMatrix,
            VietnamRegion customerRegion) {
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();
        String roundingRule = settingService.getProfitRoundingRuleRaw();
        if (profitMultiplier != null && roundingRule != null) {
            log.debug("Preparing allocation with pricing settings multiplier={} rounding={}", profitMultiplier, roundingRule);
        }

        List<SubOrderDraftDto> subOrders = new ArrayList<>();
        List<OutOfStockItemDto> outOfStockItems = new ArrayList<>();
        List<SuggestedTransferDto> suggestedTransfers = new ArrayList<>();

        if (branchesSortedByDist.isEmpty() || cart.isEmpty()) {
            return new AllocationResult(subOrders, outOfStockItems, suggestedTransfers, normalizeRegion(customerRegion), null, null, null);
        }

        List<BranchWithRealDistance> sellableBranches = branchesSortedByDist.stream()
                .filter(candidate -> candidate.branch() != null)
                .toList();

        if (sellableBranches.isEmpty()) {
            return new AllocationResult(subOrders, outOfStockItems, suggestedTransfers, normalizeRegion(customerRegion), null, null, null);
        }

        ServingBranchSelection selection = resolveServingBranchSelection(
                cart,
                sellableBranches,
                inventoryMatrix,
                customerRegion);
        if (selection == null || selection.servingBranch() == null || selection.servingBranch().branch() == null) {
            return new AllocationResult(subOrders, outOfStockItems, suggestedTransfers, normalizeRegion(customerRegion), null, null, null);
        }

        BranchWithRealDistance selectedBranchWithDistance = selection.servingBranch();
        Long selectedBranchId = selectedBranchWithDistance.branch().getId();
        Map<Long, List<Inventory>> branchBatches = inventoryMatrix.getOrDefault(selectedBranchId, Collections.emptyMap());
        Map<Long, BigDecimal> transferImportPriceCache = new HashMap<>();

        List<OrderItemDto> allocatedItems = new ArrayList<>();

        for (CartItemDto item : cart) {
            Long variantId = item.getProductVariantId();
            int requested = item.getQuantity();
            int originalRequested = requested;
            int totalAvailableAcrossAllowedBranches = calculateTotalAvailable(
                    variantId,
                    inventoryMatrix,
                    selection.accessibleBranchIds());

            List<Inventory> batches = branchBatches.getOrDefault(variantId, new ArrayList<>());
            ProductVariant variant = variantMap.get(variantId);
            String variantName = (variant != null && variant.getSku() != null) ? variant.getSku() : "Unknown";
            String variantSku = variant != null ? variant.getSku() : "";
            Long categoryId = (variant != null && variant.getProduct() != null && variant.getProduct().getCategory() != null)
                    ? variant.getProduct().getCategory().getId()
                    : null;

            int totalAllocatedForItem = 0;
            BigDecimal displayedUnitPrice = publicSellingPriceService.resolveDisplayedVariantPrice(variant);

            for (Inventory batch : batches) {
                if (requested <= 0) {
                    break;
                }

                int availableInBatch = availableForSale(batch);
                if (availableInBatch <= 0) {
                    continue;
                }

                int quantityToTake = Math.min(requested, availableInBatch);
                totalAllocatedForItem += quantityToTake;
                batch.setQuantity(availableInBatch - quantityToTake);
                requested -= quantityToTake;
            }

            if (displayedUnitPrice.compareTo(BigDecimal.ZERO) <= 0 && totalAvailableAcrossAllowedBranches > 0) {
                throw new BadRequestException("San pham " + variantName
                        + " chua co gia ban hop le. Vui long kiem tra gia nhap ton kho truoc khi dat hang.");
            }

            allocatedItems.add(OrderItemDto.builder()
                    .productVariantId(variantId)
                    .variantName(variantName)
                    .variantSku(variantSku)
                    .quantity(originalRequested)
                    .allocatedQuantity(totalAllocatedForItem)
                    .missingQuantity(requested)
                    .unitPrice(displayedUnitPrice)
                    .subtotal(displayedUnitPrice.multiply(BigDecimal.valueOf(originalRequested)))
                    .build());

            int accessibleShortage = Math.max(0, originalRequested - totalAvailableAcrossAllowedBranches);
            if (accessibleShortage > 0) {
                outOfStockItems.add(OutOfStockItemDto.builder()
                        .productVariantId(variantId)
                        .variantName(variantName)
                        .variantSku(variantSku)
                        .requestedQty(accessibleShortage)
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
                    .fromDistrictId(selectedBranchWithDistance.branch().getDistrictId())
                    .durationMinutes(selectedBranchWithDistance.durationMinutes())
                    .distanceKm(selectedBranchWithDistance.distanceKm())
                    .distanceSource(selectedBranchWithDistance.distanceSource())
                    .branchRegion(normalizeRegion(selection.servingRegion()))
                    .items(allocatedItems)
                    .subtotal(subtotal)
                    .shippingFee(BigDecimal.ZERO)
                    .build());

            suggestedTransfers.addAll(buildSuggestedTransfers(
                    selectedBranchId,
                    selection.servingRegion(),
                    allocatedItems,
                    selection.transferSources(),
                    inventoryMatrix));
        }

        return new AllocationResult(
                subOrders,
                outOfStockItems,
                suggestedTransfers,
                normalizeRegion(customerRegion),
                normalizeRegion(selection.servingRegion()),
                normalizeRegion(selection.adjacentRegionUsed()),
                selectedBranchWithDistance.distanceSource());
    }

    private ServingBranchSelection resolveServingBranchSelection(
            List<CartItemDto> cart,
            List<BranchWithRealDistance> sellableBranches,
            Map<Long, Map<Long, List<Inventory>>> inventoryMatrix,
            VietnamRegion customerRegion) {
        List<BranchWithRealDistance> directCandidates = customerRegion == null
                ? sellableBranches
                : sellableBranches.stream()
                        .filter(candidate -> vietnamRegionResolver.isSameRegion(candidate.branch(), customerRegion))
                        .toList();

        if (directCandidates.isEmpty()) {
            return null;
        }

        List<BranchWithRealDistance> servingBranchCandidates = prioritizeShippingReadyBranches(directCandidates);
        BranchWithRealDistance selectedBranchWithDistance = servingBranchCandidates.stream()
                .filter(candidate -> isBranchFullyStocked(candidate.branch().getId(), cart, inventoryMatrix))
                .findFirst()
                .orElse(servingBranchCandidates.get(0));

        VietnamRegion servingRegion = customerRegion != null
                ? customerRegion
                : vietnamRegionResolver.resolveBranchRegion(selectedBranchWithDistance.branch()).orElse(null);

        List<BranchWithRealDistance> sameRegionSources = sortByDistanceToServingBranch(
                selectedBranchWithDistance.branch(),
                directCandidates.stream()
                        .filter(candidate -> !Objects.equals(candidate.branch().getId(), selectedBranchWithDistance.branch().getId()))
                        .toList());

        VietnamRegion adjacentRegionUsed = customerRegion == null
                ? null
                : resolveAdjacentRegion(customerRegion, selectedBranchWithDistance.branch(), sellableBranches);
        List<BranchWithRealDistance> adjacentSources = adjacentRegionUsed == null
                ? List.of()
                : sortByDistanceToServingBranch(
                        selectedBranchWithDistance.branch(),
                        sellableBranches.stream()
                                .filter(candidate -> !Objects.equals(candidate.branch().getId(), selectedBranchWithDistance.branch().getId()))
                                .filter(candidate -> vietnamRegionResolver.isSameRegion(candidate.branch(), adjacentRegionUsed))
                                .toList());

        List<BranchWithRealDistance> transferSources = new ArrayList<>(sameRegionSources);
        transferSources.addAll(adjacentSources);

        Set<Long> accessibleBranchIds = new LinkedHashSet<>();
        accessibleBranchIds.add(selectedBranchWithDistance.branch().getId());
        sameRegionSources.stream()
                .map(candidate -> candidate.branch().getId())
                .forEach(accessibleBranchIds::add);
        adjacentSources.stream()
                .map(candidate -> candidate.branch().getId())
                .forEach(accessibleBranchIds::add);

        return new ServingBranchSelection(
                selectedBranchWithDistance,
                servingRegion,
                adjacentRegionUsed,
                transferSources,
                accessibleBranchIds);
    }

    private VietnamRegion resolveAdjacentRegion(
            VietnamRegion customerRegion,
            Branch servingBranch,
            List<BranchWithRealDistance> sellableBranches) {
        if (customerRegion == null || servingBranch == null) {
            return null;
        }

        if (customerRegion == VietnamRegion.NORTH || customerRegion == VietnamRegion.SOUTH) {
            List<BranchWithRealDistance> centralBranches = sellableBranches.stream()
                    .filter(candidate -> vietnamRegionResolver.isSameRegion(candidate.branch(), VietnamRegion.CENTRAL))
                    .toList();
            return centralBranches.isEmpty() ? null : VietnamRegion.CENTRAL;
        }

        BranchWithRealDistance nearestNorth = sortByDistanceToServingBranch(
                servingBranch,
                sellableBranches.stream()
                        .filter(candidate -> vietnamRegionResolver.isSameRegion(candidate.branch(), VietnamRegion.NORTH))
                        .toList())
                .stream()
                .findFirst()
                .orElse(null);
        BranchWithRealDistance nearestSouth = sortByDistanceToServingBranch(
                servingBranch,
                sellableBranches.stream()
                        .filter(candidate -> vietnamRegionResolver.isSameRegion(candidate.branch(), VietnamRegion.SOUTH))
                        .toList())
                .stream()
                .findFirst()
                .orElse(null);

        if (nearestNorth == null) {
            return nearestSouth != null ? VietnamRegion.SOUTH : null;
        }
        if (nearestSouth == null) {
            return VietnamRegion.NORTH;
        }

        double northDistance = calculateBranchDistanceKm(servingBranch, nearestNorth.branch(), nearestNorth.distanceKm());
        double southDistance = calculateBranchDistanceKm(servingBranch, nearestSouth.branch(), nearestSouth.distanceKm());
        return northDistance <= southDistance ? VietnamRegion.NORTH : VietnamRegion.SOUTH;
    }

    private List<BranchWithRealDistance> sortByDistanceToServingBranch(
            Branch servingBranch,
            List<BranchWithRealDistance> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((BranchWithRealDistance candidate) -> calculateBranchDistanceKm(
                                servingBranch,
                                candidate.branch(),
                                candidate.distanceKm()))
                        .thenComparing(candidate -> candidate.branch().getId(), Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private List<SuggestedTransferDto> buildSuggestedTransfers(
            Long destinationBranchId,
            VietnamRegion destinationRegion,
            List<OrderItemDto> allocatedItems,
            List<BranchWithRealDistance> transferSources,
            Map<Long, Map<Long, List<Inventory>>> inventoryMatrix) {
        if (destinationBranchId == null || allocatedItems == null || allocatedItems.isEmpty() || transferSources == null) {
            return List.of();
        }

        List<SuggestedTransferDto> suggestions = new ArrayList<>();
        for (OrderItemDto item : allocatedItems) {
            if (item == null || item.getProductVariantId() == null) {
                continue;
            }

            int remaining = Math.max(0, Objects.requireNonNullElse(item.getMissingQuantity(), 0));
            if (remaining <= 0) {
                continue;
            }

            for (BranchWithRealDistance branchCandidate : transferSources) {
                if (branchCandidate == null || branchCandidate.branch() == null || remaining <= 0) {
                    continue;
                }

                Long candidateBranchId = branchCandidate.branch().getId();
                int available = inventoryMatrix.getOrDefault(candidateBranchId, Collections.emptyMap())
                        .getOrDefault(item.getProductVariantId(), Collections.emptyList())
                        .stream()
                        .mapToInt(this::availableForSale)
                        .sum();
                if (available <= 0) {
                    continue;
                }

                int quantity = Math.min(available, remaining);
                suggestions.add(SuggestedTransferDto.builder()
                        .fromBranchId(candidateBranchId)
                        .fromBranchName(branchCandidate.branch().getName())
                        .toBranchId(destinationBranchId)
                        .productVariantId(item.getProductVariantId())
                        .quantity(quantity)
                        .fromRegion(normalizeRegion(vietnamRegionResolver.resolveBranchRegion(branchCandidate.branch()).orElse(null)))
                        .toRegion(normalizeRegion(destinationRegion))
                        .build());
                remaining -= quantity;
            }
        }

        return suggestions;
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

    private List<BranchWithRealDistance> prioritizeShippingReadyBranches(List<BranchWithRealDistance> branches) {
        return branches;
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

    private double calculateBranchDistanceKm(Branch fromBranch, Branch toBranch, double fallbackDistanceKm) {
        if (fromBranch == null || toBranch == null) {
            return fallbackDistanceKm;
        }

        if (fromBranch.getLat() != null && fromBranch.getLng() != null && toBranch.getLat() != null && toBranch.getLng() != null) {
            return com.zone.agri.utils.HaversineUtils.distanceKm(
                    fromBranch.getLat(),
                    fromBranch.getLng(),
                    toBranch.getLat(),
                    toBranch.getLng());
        }

        return fallbackDistanceKm;
    }

    private String normalizeRegion(VietnamRegion region) {
        return region != null ? region.name() : null;
    }
}

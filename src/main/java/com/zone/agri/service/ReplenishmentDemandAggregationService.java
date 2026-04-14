package com.zone.agri.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.zone.agri.dto.request.transfer.TransferItemRequest;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.SubOrderItemRepository;
import com.zone.agri.repository.SubOrderRepository;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplenishmentDemandAggregationService {

    private static final int AGGREGATION_WINDOW_SECONDS = 90;

    private final SubOrderRepository subOrderRepository;
    private final SubOrderItemRepository subOrderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;
    private final InventoryTransferRepository inventoryTransferRepository;
    private final InventoryTransferService inventoryTransferService;
    private final TransactionTemplate transactionTemplate;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "replenishment-aggregation-worker");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<Long, Set<Long>> pendingSubOrderIdsByBranch = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ScheduledFuture<?>> scheduledJobsByBranch = new ConcurrentHashMap<>();
    // Track branches with pending demands (Option 2: periodic flush)
    private final Set<Long> branchesWithPendingDemands = ConcurrentHashMap.newKeySet();

    public void enqueueSubOrders(Collection<SubOrder> subOrders) {
        if (subOrders == null || subOrders.isEmpty()) {
            return;
        }

        Map<Long, List<Long>> subOrderIdsByBranch = subOrders.stream()
                .filter(Objects::nonNull)
                .filter(subOrder -> subOrder.getStatus() == OrderStatus.AWAITING_REPLENISHMENT)
                .filter(subOrder -> subOrder.getBranch() != null && subOrder.getBranch().getId() != null)
                .collect(Collectors.groupingBy(
                        subOrder -> subOrder.getBranch().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(SubOrder::getId, Collectors.toList())));

        if (subOrderIdsByBranch.isEmpty()) {
            return;
        }

        subOrderIdsByBranch.forEach((branchId, subOrderIds) -> {
            pendingSubOrderIdsByBranch
                    .computeIfAbsent(branchId, key -> ConcurrentHashMap.newKeySet())
                    .addAll(subOrderIds);

            // Track this branch as having pending demands (Option 2)
            branchesWithPendingDemands.add(branchId);

            // Option 1: If only 1 sub-order, process IMMEDIATELY (no 90s wait)
            // Otherwise, schedule after AGGREGATION_WINDOW_SECONDS to aggregate with future
            // orders
            long delaySeconds = subOrderIds.size() == 1 ? 0 : AGGREGATION_WINDOW_SECONDS;

            scheduledJobsByBranch.computeIfAbsent(branchId, key -> scheduler.schedule(
                    () -> processBranchDemandWindow(branchId),
                    delaySeconds,
                    TimeUnit.SECONDS));
        });
    }

    private void processBranchDemandWindow(Long toBranchId) {
        Set<Long> queuedSubOrderIds = pendingSubOrderIdsByBranch.remove(toBranchId);
        ScheduledFuture<?> job = scheduledJobsByBranch.remove(toBranchId);
        if (job != null && !job.isDone()) {
            job.cancel(false);
        }

        if (queuedSubOrderIds == null || queuedSubOrderIds.isEmpty()) {
            return;
        }

        try {
            transactionTemplate
                    .executeWithoutResult(status -> processDemandWindowTransactional(toBranchId, queuedSubOrderIds));
        } catch (Exception ex) {
            log.warn("Aggregated replenishment processing failed for branch {}: {}", toBranchId, ex.getMessage());
        } finally {
            // Remove from pending set after processing (success or failure)
            branchesWithPendingDemands.remove(toBranchId);
        }
    }

    private void processDemandWindowTransactional(Long toBranchId, Set<Long> queuedSubOrderIds) {
        Branch toBranch = branchRepository.findById(toBranchId).orElse(null);
        if (toBranch == null || toBranch.getLat() == null || toBranch.getLng() == null) {
            log.warn("Skip aggregated replenishment: destination branch {} is missing location", toBranchId);
            return;
        }

        List<SubOrder> subOrders = subOrderRepository.findAllById(queuedSubOrderIds).stream()
                .filter(subOrder -> subOrder.getStatus() == OrderStatus.AWAITING_REPLENISHMENT)
                .filter(subOrder -> subOrder.getBranch() != null
                        && Objects.equals(subOrder.getBranch().getId(), toBranchId))
                .toList();

        if (subOrders.isEmpty()) {
            return;
        }

        Map<String, Integer> rawDemandBySku = new LinkedHashMap<>();
        for (SubOrder subOrder : subOrders) {
            List<SubOrderItem> items = subOrderItemRepository.findBySubOrderId(subOrder.getId());
            for (SubOrderItem item : items) {
                int missingQty = Objects.requireNonNullElse(item.getMissingQuantity(), 0);
                if (missingQty <= 0 || item.getProductVariant() == null || item.getProductVariant().getSku() == null) {
                    continue;
                }
                rawDemandBySku.merge(item.getProductVariant().getSku(), missingQty, Integer::sum);
            }
        }

        if (rawDemandBySku.isEmpty()) {
            return;
        }

        Map<String, Integer> inFlightBySku = loadInFlightQuantityBySku(toBranchId, rawDemandBySku.keySet());
        Map<String, Integer> netDemandBySku = new LinkedHashMap<>();
        rawDemandBySku.forEach((sku, demandQty) -> {
            int inFlight = inFlightBySku.getOrDefault(sku, 0);
            int remaining = Math.max(0, demandQty - inFlight);
            if (remaining > 0) {
                netDemandBySku.put(sku, remaining);
            }
        });

        if (netDemandBySku.isEmpty()) {
            return;
        }

        Map<Long, Map<String, Integer>> transferPlanBySourceBranch = buildTransferPlanBySourceBranch(toBranch,
                netDemandBySku);
        if (transferPlanBySourceBranch.isEmpty()) {
            log.info("No available source stock for aggregated replenishment at branch {}", toBranchId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String referenceCode = buildAggregatedReferenceCode(toBranchId, now);

        for (Map.Entry<Long, Map<String, Integer>> entry : transferPlanBySourceBranch.entrySet()) {
            List<TransferItemRequest> items = entry.getValue().entrySet().stream()
                    .map(it -> {
                        TransferItemRequest req = new TransferItemRequest();
                        req.setSku(it.getKey());
                        req.setQuantity(it.getValue());
                        req.setItemNote("Gom nhu cầu bổ sung tự động theo cửa sổ thời gian");
                        return req;
                    })
                    .toList();

            TransferRequest request = new TransferRequest();
            request.setFromBranchId(entry.getKey());
            request.setToBranchId(toBranchId);
            request.setTransferType("ORDER_REPLENISHMENT");
            request.setDescription("Gom nhu cầu bổ sung tự động cho chi nhánh " + toBranch.getName());
            request.setReferenceCode(referenceCode);
            request.setPriority("HIGH");
            request.setTransferDate(now);
            request.setDeadline(now.plusDays(1));
            request.setItems(items);

            inventoryTransferService.createTransfer(request);
        }

        log.info("Created aggregated replenishment transfers for branch {} with {} SKU(s)", toBranchId,
                netDemandBySku.size());
    }

    private Map<String, Integer> loadInFlightQuantityBySku(Long toBranchId, Set<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> rows = inventoryTransferRepository.sumInFlightQuantityByToBranchAndSkuIn(
                toBranchId,
                List.of(InventoryTransferStatus.PENDING, InventoryTransferStatus.SHIPPING),
                skus);

        Map<String, Integer> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String sku = row[0] != null ? row[0].toString() : null;
            int qty = row[1] instanceof Number ? ((Number) row[1]).intValue() : 0;
            if (sku != null && qty > 0) {
                result.put(sku, qty);
            }
        }
        return result;
    }

    private Map<Long, Map<String, Integer>> buildTransferPlanBySourceBranch(Branch toBranch,
            Map<String, Integer> netDemandBySku) {
        Map<Long, Map<String, Integer>> plan = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> demandEntry : netDemandBySku.entrySet()) {
            String sku = demandEntry.getKey();
            int remaining = demandEntry.getValue();

            List<Inventory> inventories = productVariantRepository.findBySku(sku)
                    .map(variant -> inventoryRepository.findByProductVariantId(variant.getId()))
                    .orElse(Collections.emptyList())
                    .stream()
                    .filter(inv -> inv.getBranch() != null)
                    .filter(inv -> !Objects.equals(inv.getBranch().getId(), toBranch.getId()))
                    .filter(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0) > 0)
                    .toList();

            Map<Long, Integer> availableByBranch = inventories.stream()
                    .collect(Collectors.groupingBy(
                            inv -> inv.getBranch().getId(),
                            LinkedHashMap::new,
                            Collectors.summingInt(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0))));

            List<Map.Entry<Long, Integer>> sortedCandidates = new ArrayList<>(availableByBranch.entrySet());
            sortedCandidates.sort((a, b) -> Double.compare(
                    distanceToDestinationKm(toBranch, a.getKey()),
                    distanceToDestinationKm(toBranch, b.getKey())));

            for (Map.Entry<Long, Integer> candidate : sortedCandidates) {
                if (remaining <= 0) {
                    break;
                }

                int toMove = Math.min(remaining, candidate.getValue());
                if (toMove <= 0) {
                    continue;
                }

                plan.computeIfAbsent(candidate.getKey(), key -> new LinkedHashMap<>())
                        .merge(sku, toMove, Integer::sum);
                remaining -= toMove;
            }
        }

        return plan;
    }

    private double distanceToDestinationKm(Branch toBranch, Long sourceBranchId) {
        Branch source = branchRepository.findById(sourceBranchId).orElse(null);
        if (source == null || source.getLat() == null || source.getLng() == null) {
            return Double.MAX_VALUE;
        }
        return haversineKm(toBranch.getLat(), toBranch.getLng(), source.getLat(), source.getLng());
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double radiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return radiusKm * c;
    }

    private String buildAggregatedReferenceCode(Long toBranchId, LocalDateTime now) {
        long minuteBucket = now.withSecond(0).withNano(0)
                .atZone(ZoneId.systemDefault())
                .toEpochSecond() / 60;
        return "AUTO-AGG-B" + toBranchId + "-" + minuteBucket;
    }

    // Option 2: Periodic flush - every 10 minutes, force process all pending
    // demands
    // This prevents sub-orders from waiting too long (e.g., 2-3 days)
    // It's a safety valve to ensure stale queued demands get processed
    @Scheduled(fixedRate = 600000) // 10 minutes = 600000 ms
    public void periodicFlushPendingDemands() {
        Set<Long> branchesToFlush = new java.util.HashSet<>(branchesWithPendingDemands);
        if (branchesToFlush.isEmpty()) {
            return;
        }

        for (Long branchId : branchesToFlush) {
            // Cancel pending scheduled job (if it hasn't fired yet)
            ScheduledFuture<?> job = scheduledJobsByBranch.remove(branchId);
            if (job != null && !job.isDone()) {
                job.cancel(false);
            }

            // Force flush all pending sub-orders for this branch
            Set<Long> queuedSubOrderIds = pendingSubOrderIdsByBranch.get(branchId);
            if (queuedSubOrderIds != null && !queuedSubOrderIds.isEmpty()) {
                log.info(
                        "[Periodic Flush] Processing {} pending sub-orders for branch {} (avoiding stale queue > 10min)",
                        queuedSubOrderIds.size(), branchId);
                processBranchDemandWindow(branchId);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}

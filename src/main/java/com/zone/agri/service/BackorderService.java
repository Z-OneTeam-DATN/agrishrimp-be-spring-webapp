package com.zone.agri.service;

import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.SubOrderItemRepository;
import com.zone.agri.repository.SubOrderRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BackorderService {

    private final SubOrderItemRepository subOrderItemRepository;
    private final SubOrderRepository subOrderRepository;
    private final InventoryCheckGuardService inventoryCheckGuardService;
    private final OrderInventoryReservationService orderInventoryReservationService;
    private final OrderStatusSyncService orderStatusSyncService;

    public BackorderService(
            SubOrderItemRepository subOrderItemRepository,
            SubOrderRepository subOrderRepository,
            InventoryCheckGuardService inventoryCheckGuardService,
            OrderInventoryReservationService orderInventoryReservationService,
            @Lazy OrderStatusSyncService orderStatusSyncService) {
        this.subOrderItemRepository = subOrderItemRepository;
        this.subOrderRepository = subOrderRepository;
        this.inventoryCheckGuardService = inventoryCheckGuardService;
        this.orderInventoryReservationService = orderInventoryReservationService;
        this.orderStatusSyncService = orderStatusSyncService;
    }

    @Transactional
    public void fulfillBackordersOnStockReceive(Long branchId, Long productVariantId, int newQuantityAdded) {
        if (branchId == null || productVariantId == null || newQuantityAdded <= 0) {
            return;
        }

        List<SubOrderItem> waitingItems = subOrderItemRepository.findBackorderItemsForFulfillment(
                branchId,
                productVariantId);
        if (waitingItems.isEmpty()) {
            return;
        }

        int availableToAllocate = newQuantityAdded;
        Set<Long> touchedSubOrderIds = new HashSet<>();
        Set<Long> affectedOrderIds = new HashSet<>();

        for (SubOrderItem item : waitingItems) {
            if (availableToAllocate <= 0) {
                break;
            }

            int missingQty = Objects.requireNonNullElse(item.getMissingQuantity(), 0);
            if (missingQty <= 0) {
                continue;
            }

            int qtyToFulfill = Math.min(missingQty, availableToAllocate);
            inventoryCheckGuardService.assertStockMutationAllowed(
                    item.getSubOrder().getBranch().getId(),
                    List.of(item.getProductVariant().getId()),
                    "cap bu backorder");
            orderInventoryReservationService.reserveInventory(
                    item.getSubOrder().getBranch().getId(),
                    item.getProductVariant().getId(),
                    qtyToFulfill,
                    buildSubOrderReferenceCode(item.getSubOrder()),
                    "Giu hang bo sung cho phan don " + item.getSubOrder().getOrder().getCode());

            item.setAllocatedQuantity(Objects.requireNonNullElse(item.getAllocatedQuantity(), 0) + qtyToFulfill);
            item.setMissingQuantity(missingQty - qtyToFulfill);
            subOrderItemRepository.save(item);

            availableToAllocate -= qtyToFulfill;
            touchedSubOrderIds.add(item.getSubOrder().getId());
            affectedOrderIds.add(item.getSubOrder().getOrder().getId());
        }

        for (Long subOrderId : touchedSubOrderIds) {
            List<SubOrderItem> items = subOrderItemRepository.findBySubOrderId(subOrderId);
            boolean fullyAllocated = items.stream()
                    .allMatch(i -> Objects.requireNonNullElse(i.getMissingQuantity(), 0) == 0);

            if (fullyAllocated) {
                SubOrder subOrder = subOrderRepository.findById(subOrderId)
                        .orElseThrow(() -> new NotFoundException("Khong tim thay sub-order ID: " + subOrderId));
                if (subOrder.getStatus() == OrderStatus.AWAITING_REPLENISHMENT) {
                    subOrder.setStatus(OrderStatus.PROCESSING);
                    subOrderRepository.save(subOrder);
                    log.info("Backorder fulfilled for sub-order {} and resumed to PROCESSING", subOrderId);
                }
            }
        }

        affectedOrderIds.forEach(orderStatusSyncService::syncMasterOrderStatus);
    }

    private String buildSubOrderReferenceCode(SubOrder subOrder) {
        return subOrder.getOrder().getCode() + "-SUB-" + subOrder.getId();
    }
}

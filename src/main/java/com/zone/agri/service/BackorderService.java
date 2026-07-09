package com.zone.agri.service;

import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.SubOrderItemRepository;
import com.zone.agri.repository.SubOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class BackorderService {

    private final SubOrderItemRepository subOrderItemRepository;
    private final SubOrderRepository subOrderRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final OrderStatusSyncService orderStatusSyncService;

    public BackorderService(
            SubOrderItemRepository subOrderItemRepository,
            SubOrderRepository subOrderRepository,
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository transactionRepository,
            @Lazy OrderStatusSyncService orderStatusSyncService) {
        this.subOrderItemRepository = subOrderItemRepository;
        this.subOrderRepository = subOrderRepository;
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.orderStatusSyncService = orderStatusSyncService;
    }

    @Transactional
    public void fulfillBackordersOnStockReceive(Long branchId, Long productVariantId, int newQuantityAdded) {
        if (branchId == null || productVariantId == null || newQuantityAdded <= 0) {
            return;
        }

        List<SubOrderItem> waitingItems = subOrderItemRepository.findBackorderItemsForFulfillment(branchId,
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
            deductInventoryForBackorder(item.getSubOrder(), item.getProductVariant().getId(), qtyToFulfill);

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
                    log.info("Backorder fulfilled for sub-order {}", subOrderId);
                }
            }
        }

        affectedOrderIds.forEach(orderStatusSyncService::syncMasterOrderStatus);
    }

    private void deductInventoryForBackorder(SubOrder subOrder, Long productVariantId, int quantityToDeduct) {
        int remainingToDeduct = quantityToDeduct;
        List<Inventory> batches = inventoryRepository.findForUpdateFIFO(subOrder.getBranch().getId(), productVariantId);

        for (Inventory batch : batches) {
            if (remainingToDeduct <= 0) {
                break;
            }

            int available = Objects.requireNonNullElse(batch.getQuantity(), 0);
            if (available <= 0) {
                continue;
            }

            int deductAmount = Math.min(available, remainingToDeduct);
            int newQty = available - deductAmount;
            batch.setQuantity(newQty);
            inventoryRepository.save(batch);

            transactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.SALE)
                    .quantityChange(-deductAmount)
                    .newBalance(newQty)
                    .referenceCode(buildSubOrderReferenceCode(subOrder))
                    .reason("Ban hang bo sung do tra no backorder")
                    .createdAt(LocalDateTime.now())
                    .inventory(batch)
                    .build());

            remainingToDeduct -= deductAmount;
        }

        if (remainingToDeduct > 0) {
            throw new ConflictException(
                    "Ton kho da thay doi trong luc tra no backorder cho sub-order " + subOrder.getId(), true);
        }
    }

    private String buildSubOrderReferenceCode(SubOrder subOrder) {
        return subOrder.getOrder().getCode() + "-SUB-" + subOrder.getId();
    }

}

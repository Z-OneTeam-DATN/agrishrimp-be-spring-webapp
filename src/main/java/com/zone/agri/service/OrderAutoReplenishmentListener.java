package com.zone.agri.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.event.OrderAwaitingReplenishmentCreatedEvent;
import com.zone.agri.repository.SubOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAutoReplenishmentListener {

    private final SubOrderRepository subOrderRepository;
    private final InventoryTransferService inventoryTransferService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderAwaitingReplenishmentCreated(OrderAwaitingReplenishmentCreatedEvent event) {
        List<SubOrder> awaitingSubOrders = subOrderRepository.findByOrderId(event.orderId()).stream()
                .filter(subOrder -> subOrder.getStatus() == OrderStatus.AWAITING_REPLENISHMENT)
                .toList();

        if (awaitingSubOrders.isEmpty()) {
            return;
        }

        int createdTransferCount = 0;
        for (SubOrder subOrder : awaitingSubOrders) {
            try {
                createdTransferCount += inventoryTransferService.createReplenishmentTransfersForSubOrder(subOrder).size();
            } catch (Exception ex) {
                log.warn("Failed to auto-create replenishment transfer for sub-order {}: {}",
                        subOrder.getId(), ex.getMessage());
            }
        }

        log.info("Created {} replenishment transfer(s) immediately for {} awaiting sub-order(s)",
                createdTransferCount, awaitingSubOrders.size());
    }
}

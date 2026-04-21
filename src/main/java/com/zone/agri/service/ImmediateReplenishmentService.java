package com.zone.agri.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.zone.agri.entity.SubOrder;
import com.zone.agri.repository.SubOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImmediateReplenishmentService {

    private final SubOrderRepository subOrderRepository;
    private final InventoryTransferService inventoryTransferService;

    public void scheduleAfterCommit(List<Long> awaitingSubOrderIds, String orderCode) {
        if (awaitingSubOrderIds == null || awaitingSubOrderIds.isEmpty()) {
            return;
        }

        List<Long> distinctSubOrderIds = new ArrayList<>(new LinkedHashSet<>(awaitingSubOrderIds));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            createTransfers(distinctSubOrderIds, orderCode);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                createTransfers(distinctSubOrderIds, orderCode);
            }
        });
    }

    public void createTransfers(List<Long> awaitingSubOrderIds, String orderCode) {
        int ensuredTransferCount = 0;

        for (Long subOrderId : awaitingSubOrderIds) {
            try {
                SubOrder subOrder = subOrderRepository.findById(subOrderId)
                        .orElseThrow(() -> new RuntimeException(
                                "Khong tim thay phan don can tao dieu chuyen bo sung: " + subOrderId));
                ensuredTransferCount += inventoryTransferService.createReplenishmentTransfersForSubOrder(subOrder).size();
            } catch (Exception ex) {
                log.warn(
                        "Failed to auto-create replenishment transfer for order {} sub-order {}: {}",
                        orderCode,
                        subOrderId,
                        ex.getMessage());
            }
        }

        log.info("Ensured {} replenishment transfer(s) for order {} across {} awaiting sub-order(s)",
                ensuredTransferCount, orderCode, awaitingSubOrderIds.size());
    }
}

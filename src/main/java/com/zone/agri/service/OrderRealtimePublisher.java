package com.zone.agri.service;

import com.zone.agri.dto.response.order.OrderRealtimeEvent;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final OrderRepository orderRepository;
    private final PlatformTransactionManager transactionManager;

    public void publishOrderChangedAfterCommit(Long orderId, String eventType) {
        if (orderId == null) {
            return;
        }

        Runnable publishAction = () -> publishOrderChangedNow(orderId, eventType);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAction.run();
                }
            });
            return;
        }

        publishAction.run();
    }

    private void publishOrderChangedNow(Long orderId, String eventType) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        transactionTemplate.executeWithoutResult(status -> orderRepository
                .findByIdWithRealtimeContext(orderId)
                .ifPresentOrElse(
                        order -> publish(order, eventType),
                        () -> log.debug("Skip realtime publish because order {} no longer exists", orderId)));
    }

    private void publish(Order order, String eventType) {
        Set<Long> branchIds = resolveBranchIds(order);
        OrderRealtimeEvent event = OrderRealtimeEvent.builder()
                .eventType(eventType)
                .orderId(order.getId())
                .orderCode(order.getCode())
                .branchIds(branchIds)
                .orderStatus(order.getStatus() != null ? order.getStatus().name() : null)
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .occurredAt(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/orders/all", event);
        branchIds.forEach(branchId ->
                messagingTemplate.convertAndSend("/topic/orders/branch/" + branchId, event));
    }

    private Set<Long> resolveBranchIds(Order order) {
        Set<Long> branchIds = new LinkedHashSet<>();
        if (order.getSubOrders() != null) {
            order.getSubOrders().stream()
                    .map(SubOrder::getBranch)
                    .filter(Objects::nonNull)
                    .map(branch -> branch.getId())
                    .filter(Objects::nonNull)
                    .forEach(branchIds::add);
        }

        if (branchIds.isEmpty() && order.getBranch() != null && order.getBranch().getId() != null) {
            branchIds.add(order.getBranch().getId());
        }

        return branchIds;
    }
}

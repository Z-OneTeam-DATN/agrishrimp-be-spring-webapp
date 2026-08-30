package com.zone.agri.service;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.enums.FulfillmentStatus;
import com.zone.agri.entity.enums.OrderCancelReasonCode;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.entity.enums.StockStatus;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SubOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderStatusSyncService {

    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final VoucherService voucherService;
    private final PayOSService payOSService;
    private final NotificationService notificationService;
    private final OrderRealtimePublisher orderRealtimePublisher;

    @Lazy
    private final CustomerService customerService;

    @Transactional
    public void syncMasterOrderStatus(Long orderId) {
        syncMasterOrderStatus(orderId, true);
    }

    @Transactional
    public void syncMasterOrderStatus(Long orderId, boolean sendCustomerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Khong tim thay don hang tong"));
        OrderStatus previousStatus = order.getStatus();

        List<SubOrder> allSubs = subOrderRepository.findByOrderId(orderId);
        if (allSubs.isEmpty()) {
            return;
        }

        List<SubOrder> activeSubs = allSubs.stream()
                .filter(subOrder -> subOrder.getStatus() != OrderStatus.CANCELLED
                        && subOrder.getStatus() != OrderStatus.RETURNED)
                .toList();
        List<SubOrder> nonCancelledSubs = allSubs.stream()
                .filter(subOrder -> subOrder.getStatus() != OrderStatus.CANCELLED)
                .toList();

        OrderStatus nextStatus;
        if (!nonCancelledSubs.isEmpty()
                && nonCancelledSubs.stream().allMatch(subOrder -> subOrder.getStatus() == OrderStatus.RETURNED)) {
            nextStatus = OrderStatus.RETURNED;
        } else if (activeSubs.isEmpty()) {
            nextStatus = OrderStatus.CANCELLED;
        } else if (activeSubs.stream().allMatch(subOrder -> subOrder.getStatus() == OrderStatus.COMPLETED)) {
            nextStatus = OrderStatus.COMPLETED;
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if (activeSubs.stream().allMatch(
                subOrder -> subOrder.getStatus() == OrderStatus.RECEIVED
                        || subOrder.getStatus() == OrderStatus.COMPLETED)) {
            nextStatus = OrderStatus.RECEIVED;
            order.setPaymentStatus(PaymentStatus.PAID);
        } else {
            nextStatus = activeSubs.stream()
                    .map(SubOrder::getStatus)
                    .min(Comparator.comparingInt(this::statusWeight))
                    .orElse(OrderStatus.PENDING);
        }

        if (nextStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            voucherService.restoreVoucherForOrder(order);
            if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                    && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
                payOSService.cancelPaymentLink(order);
            }
            if (order.getCancelReasonCode() == null) {
                order.setCancelReasonCode(OrderCancelReasonCode.SUB_ORDERS_CANCELLED);
            }
        }

        applyOrderStatus(order, nextStatus, LocalDateTime.now());
        order.setStockStatus(resolveStockStatus(order, activeSubs));
        orderRepository.saveAndFlush(order);

        if ((nextStatus == OrderStatus.COMPLETED
                || nextStatus == OrderStatus.CANCELLED
                || nextStatus == OrderStatus.RETURNED)
                && order.getUser() != null) {
            customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
        }
        notificationService.notifyOrderStatusChange(order, previousStatus, nextStatus, sendCustomerEmail);
        orderRealtimePublisher.publishOrderChangedAfterCommit(orderId, "ORDER_UPDATED");
    }

    private void applyOrderStatus(Order order, OrderStatus status, LocalDateTime changedAt) {
        order.setStatus(status);
        switch (status) {
            case PROCESSING -> order.setFulfillmentStatus(FulfillmentStatus.PREPARING);
            case READY_FOR_PICKUP -> order.setFulfillmentStatus(FulfillmentStatus.READY_TO_SHIP);
            case SHIPPING -> {
                order.setFulfillmentStatus(FulfillmentStatus.SHIPPING);
                if (order.getShippingStartedAt() == null) {
                    order.setShippingStartedAt(changedAt);
                }
            }
            case RECEIVED, COMPLETED -> order.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
            case RETURNED -> order.setFulfillmentStatus(FulfillmentStatus.RETURNED);
            default -> order.setFulfillmentStatus(FulfillmentStatus.NOT_STARTED);
        }
        if (status == OrderStatus.RECEIVED && order.getReceivedAt() == null) {
            order.setReceivedAt(changedAt);
        }
        if (status == OrderStatus.COMPLETED) {
            if (order.getCompletedAt() == null) {
                order.setCompletedAt(changedAt);
            }
        }
        if (status == OrderStatus.RETURNED && order.getReturnedAt() == null) {
            order.setReturnedAt(changedAt);
        }
        if (status == OrderStatus.CANCELLED && order.getCancelledAt() == null) {
            order.setCancelledAt(changedAt);
        }
    }

    private StockStatus resolveStockStatus(Order order, List<SubOrder> activeSubs) {
        boolean hasMissingItems = activeSubs.stream()
                .filter(Objects::nonNull)
                .flatMap(subOrder -> subOrder.getItems() == null ? java.util.stream.Stream.empty() : subOrder.getItems().stream())
                .anyMatch(this::hasMissingQuantity);

        if (!hasMissingItems) {
            return StockStatus.FULLY_AVAILABLE;
        }

        if (order.getStockStatus() == StockStatus.OUT_OF_STOCK) {
            return StockStatus.OUT_OF_STOCK;
        }

        if (order.getStockStatus() == StockStatus.AVAILABLE_AFTER_TRANSFER) {
            return StockStatus.AVAILABLE_AFTER_TRANSFER;
        }

        return StockStatus.PARTIALLY_AVAILABLE;
    }

    private boolean hasMissingQuantity(SubOrderItem item) {
        return item != null && Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0;
    }

    private int statusWeight(OrderStatus status) {
        return switch (Objects.requireNonNullElse(status, OrderStatus.PENDING)) {
            case AWAITING_PAYMENT -> 0;
            case PENDING -> 1;
            case AWAITING_REPLENISHMENT -> 2;
            case CONFIRMED -> 3;
            case PROCESSING -> 4;
            case READY_FOR_PICKUP -> 5;
            case SHIPPING -> 6;
            case RECEIVED -> 7;
            case COMPLETED -> 8;
            default -> 9;
        };
    }
}

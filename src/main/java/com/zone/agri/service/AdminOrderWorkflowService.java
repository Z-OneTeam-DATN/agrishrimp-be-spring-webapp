package com.zone.agri.service;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminOrderWorkflowService {

    private static final Set<OrderStatus> CLOSED_STATUSES = EnumSet.of(
            OrderStatus.CANCELLED,
            OrderStatus.COMPLETED,
            OrderStatus.RETURNED);

    private static final Set<OrderStatus> SHIPPABLE_MASTER_STATUSES = EnumSet.of(
            OrderStatus.READY_FOR_PICKUP);

    private final OrderRepository orderRepository;
    private final OrderInventoryReservationService orderInventoryReservationService;
    private final NotificationService notificationService;
    private final OrderRealtimePublisher orderRealtimePublisher;

    @Transactional
    public void approvePackingAndShip(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Khong tim thay don hang ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == null || CLOSED_STATUSES.contains(currentStatus)) {
            throw new BadRequestException("Don hang da dong, khong the chuyen sang dang giao.");
        }

        if (!SHIPPABLE_MASTER_STATUSES.contains(currentStatus)) {
            throw new BadRequestException(
                    "Admin chi co the chuyen don sang dang giao khi don dang o trang thai cho ban giao.");
        }

        List<SubOrder> activeSubOrders = order.getSubOrders() == null
                ? Collections.emptyList()
                : order.getSubOrders().stream()
                        .filter(subOrder -> !CLOSED_STATUSES.contains(subOrder.getStatus()))
                        .toList();

        if (!activeSubOrders.isEmpty()) {
            throw new BadRequestException(
                    "Don nay dang chay theo luong chi nhanh. Vui long ban giao bang phieu handover thay vi chuyen thang sang SHIPPING.");
        }

        orderInventoryReservationService.shipReservedInventory(
                orderInventoryReservationService.buildOrderReferenceCode(order),
                "Xuat kho don hang " + order.getCode() + " khi admin chuyen sang giao hang");
        order.setStatus(OrderStatus.SHIPPING);
        orderRepository.save(order);
        notificationService.notifyOrderStatusChange(order, currentStatus, OrderStatus.SHIPPING);
        orderRealtimePublisher.publishOrderChangedAfterCommit(order.getId(), "ORDER_UPDATED");
    }
}

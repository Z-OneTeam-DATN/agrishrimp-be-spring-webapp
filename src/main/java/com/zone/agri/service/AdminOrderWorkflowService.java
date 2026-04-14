package com.zone.agri.service;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SubOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOrderWorkflowService {

    private static final Set<OrderStatus> CLOSED_STATUSES = EnumSet.of(
            OrderStatus.CANCELLED,
            OrderStatus.COMPLETED,
            OrderStatus.RETURNED);

    private static final Set<OrderStatus> SHIPPABLE_MASTER_STATUSES = EnumSet.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.READY_FOR_PICKUP);

    private static final Set<OrderStatus> SHIPPABLE_SUB_STATUSES = EnumSet.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.SHIPPING);

    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;

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
                    "Admin chi co the duyet dong goi va chuyen van chuyen cho don da xac nhan, dang xu ly hoac cho lay hang.");
        }

        List<SubOrder> activeSubOrders = order.getSubOrders() == null
                ? Collections.emptyList()
                : order.getSubOrders().stream()
                        .filter(subOrder -> !CLOSED_STATUSES.contains(subOrder.getStatus()))
                        .collect(Collectors.toList());

        if (!activeSubOrders.isEmpty()) {
            boolean hasInvalidSubOrder = activeSubOrders.stream()
                    .map(SubOrder::getStatus)
                    .anyMatch(status -> status == null || !SHIPPABLE_SUB_STATUSES.contains(status));

            if (hasInvalidSubOrder) {
                throw new BadRequestException(
                        "Khong the chuyen don sang dang giao vi van con phan don chua san sang.");
            }

            List<SubOrder> subOrdersToShip = activeSubOrders.stream()
                    .filter(subOrder -> subOrder.getStatus() != OrderStatus.SHIPPING)
                    .collect(Collectors.toList());

            if (!subOrdersToShip.isEmpty()) {
                subOrdersToShip.forEach(subOrder -> subOrder.setStatus(OrderStatus.SHIPPING));
                subOrderRepository.saveAll(subOrdersToShip);
            }
        }

        order.setStatus(OrderStatus.SHIPPING);
        orderRepository.save(order);
    }
}

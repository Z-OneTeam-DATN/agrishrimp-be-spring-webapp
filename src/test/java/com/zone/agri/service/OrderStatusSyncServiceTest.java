package com.zone.agri.service;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SubOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusSyncServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private SubOrderRepository subOrderRepository;
    @Mock
    private VoucherService voucherService;
    @Mock
    private PayOSService payOSService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private OrderRealtimePublisher orderRealtimePublisher;
    @Mock
    private CustomerService customerService;

    private OrderStatusSyncService orderStatusSyncService;

    @BeforeEach
    void setUp() {
        orderStatusSyncService = new OrderStatusSyncService(
                orderRepository,
                subOrderRepository,
                voucherService,
                payOSService,
                notificationService,
                orderRealtimePublisher,
                customerService);
    }

    @Test
    void syncMasterOrderStatus_allActiveSubOrdersCompleted_marksMasterCompletedAndNotifies() {
        User customer = User.builder().id(25L).build();
        Order order = Order.builder()
                .id(101L)
                .code("ORD-101")
                .status(OrderStatus.SHIPPING)
                .paymentMethod(PaymentMethod.COD)
                .user(customer)
                .build();
        SubOrder firstSubOrder = SubOrder.builder()
                .id(201L)
                .order(order)
                .status(OrderStatus.COMPLETED)
                .build();
        SubOrder secondSubOrder = SubOrder.builder()
                .id(202L)
                .order(order)
                .status(OrderStatus.COMPLETED)
                .build();

        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(subOrderRepository.findByOrderId(101L)).thenReturn(List.of(firstSubOrder, secondSubOrder));

        orderStatusSyncService.syncMasterOrderStatus(101L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderRepository).saveAndFlush(order);
        verify(customerService).evaluateAndHandleCustomerReputation(25L);
        verify(notificationService).notifyOrderStatusChange(order, OrderStatus.SHIPPING, OrderStatus.COMPLETED, true);
        verify(orderRealtimePublisher).publishOrderChangedAfterCommit(101L, "ORDER_UPDATED");
    }
}

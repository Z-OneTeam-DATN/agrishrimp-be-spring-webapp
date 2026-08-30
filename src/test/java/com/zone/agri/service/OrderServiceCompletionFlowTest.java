package com.zone.agri.service;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.CartItemRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.OrderItemRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.ReviewRepository;
import com.zone.agri.repository.SubOrderItemRepository;
import com.zone.agri.repository.SubOrderRepository;
import com.zone.agri.repository.UserAddressRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.repository.UserVoucherRepository;
import com.zone.agri.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceCompletionFlowTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private SubOrderRepository subOrderRepository;
    @Mock
    private SubOrderItemRepository subOrderItemRepository;
    @Mock
    private InventoryTransferRepository inventoryTransferRepository;
    @Mock
    private InventoryCheckGuardService inventoryCheckGuardService;
    @Mock
    private UserAddressRepository userAddressRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private UserVoucherRepository userVoucherRepository;
    @Mock
    private BranchSearchService branchSearchService;
    @Mock
    private InventoryAllocationService allocationService;
    @Mock
    private ShippingService shippingService;
    @Mock
    private PayOSService payOSService;
    @Mock
    private SettingService settingService;
    @Mock
    private InventoryTransferService inventoryTransferService;
    @Mock
    private PurchaseRequestService purchaseRequestService;
    @Mock
    private BackorderService backorderService;
    @Mock
    private ImmediateReplenishmentService immediateReplenishmentService;
    @Mock
    private VoucherService voucherService;
    @Mock
    private OrderStatusSyncService orderStatusSyncService;
    @Mock
    private OrderInventoryReservationService orderInventoryReservationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private OrderRealtimePublisher orderRealtimePublisher;
    @Mock
    private PublicSellingPriceService publicSellingPriceService;
    @Mock
    private CustomerService customerService;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subOrderRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subOrderRepository.saveAndFlush(any(SubOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateOrderStatus_shippingToCompleted_completesMasterOrderImmediately() {
        User customer = User.builder().id(11L).build();
        Order order = Order.builder()
                .id(101L)
                .code("ORD-101")
                .status(OrderStatus.SHIPPING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .user(customer)
                .build();
        SubOrder subOrder = SubOrder.builder()
                .id(201L)
                .order(order)
                .status(OrderStatus.SHIPPING)
                .build();
        order.setSubOrders(List.of(subOrder));

        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(101L, OrderStatus.COMPLETED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(subOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(customerService).evaluateAndHandleCustomerReputation(11L);
        verify(notificationService).notifyOrderStatusChange(order, OrderStatus.SHIPPING, OrderStatus.COMPLETED, false);
        verify(orderStatusSyncService, never()).syncMasterOrderStatus(any());
    }

    @Test
    void confirmReceivedByCustomer_shippingCompletesOrderAndNotifiesOnce() {
        User customer = User.builder().id(22L).build();
        Order order = Order.builder()
                .id(202L)
                .code("ORD-202")
                .status(OrderStatus.SHIPPING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .user(customer)
                .build();

        when(orderRepository.findById(202L)).thenReturn(Optional.of(order));

        orderService.confirmReceivedByCustomer(22L, 202L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getReceivedAt()).isNotNull();
        verify(customerService).evaluateAndHandleCustomerReputation(22L);
        verify(notificationService).notifyOrderStatusChange(order, OrderStatus.SHIPPING, OrderStatus.COMPLETED, true);
    }

    @Test
    void confirmReceivedByCustomer_completedWithinWindow_setsReceiptAndEmailsOnce() {
        User customer = User.builder().id(23L).build();
        Order order = Order.builder()
                .id(203L)
                .code("ORD-203")
                .status(OrderStatus.COMPLETED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .shippingStartedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(1))
                .user(customer)
                .build();

        when(orderRepository.findById(203L)).thenReturn(Optional.of(order));

        orderService.confirmReceivedByCustomer(23L, 203L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getReceivedAt()).isNotNull();
        verify(notificationService).notifyCustomerReceiptConfirmed(order);
    }

    @Test
    void confirmReceivedByCustomer_whenAlreadyReceived_isIdempotent() {
        User customer = User.builder().id(24L).build();
        Order order = Order.builder()
                .id(204L)
                .code("ORD-204")
                .status(OrderStatus.COMPLETED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PAID)
                .shippingStartedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(1))
                .receivedAt(LocalDateTime.now().minusMinutes(30))
                .user(customer)
                .build();

        when(orderRepository.findById(204L)).thenReturn(Optional.of(order));

        orderService.confirmReceivedByCustomer(24L, 204L);

        verify(notificationService, never()).notifyCustomerReceiptConfirmed(any());
        verify(notificationService, never()).notifyOrderStatusChange(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void updateSubOrderStatus_shippingToCompleted_completesOnlyBranchSubOrderAndSyncsMaster() {
        User customer = User.builder().id(33L).build();
        Order order = Order.builder()
                .id(303L)
                .code("ORD-303")
                .status(OrderStatus.SHIPPING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .user(customer)
                .build();
        SubOrder targetSubOrder = SubOrder.builder()
                .id(401L)
                .order(order)
                .status(OrderStatus.SHIPPING)
                .build();
        SubOrder otherSubOrder = SubOrder.builder()
                .id(402L)
                .order(order)
                .status(OrderStatus.SHIPPING)
                .build();
        order.setSubOrders(List.of(targetSubOrder, otherSubOrder));

        when(subOrderRepository.findByOrderIdAndBranchId(303L, 77L)).thenReturn(Optional.of(targetSubOrder));

        orderService.updateSubOrderStatus(77L, 303L, OrderStatus.COMPLETED);

        assertThat(targetSubOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(otherSubOrder.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(subOrderRepository).saveAndFlush(targetSubOrder);
        verify(orderRepository).save(order);
        verify(orderStatusSyncService).syncMasterOrderStatus(303L, false);
        verify(notificationService, never()).notifyOrderStatusChange(any(), any(), any());
    }
}

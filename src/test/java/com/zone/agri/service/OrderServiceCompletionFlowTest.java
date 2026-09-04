package com.zone.agri.service;

import com.zone.agri.dto.request.order.OrderCancelRequest;
import com.zone.agri.dto.response.order.BranchOrderResponse;
import com.zone.agri.dto.response.order.OrderResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.CartItemRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.OrderItemRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.ReturnRequestRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
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

    @Test
    void cancelMyOrder_withinFiveMinuteWindow_cancelsPendingOrder() {
        User customer = User.builder().id(41L).build();
        Order order = Order.builder()
                .id(501L)
                .code("ORD-501")
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .createdAt(LocalDateTime.now().minusMinutes(4))
                .user(customer)
                .build();

        when(orderRepository.findById(501L)).thenReturn(Optional.of(order));
        when(transactionRepository.findByReferenceCodeAndType("ORD-501", TransactionType.SALE)).thenReturn(List.of());

        orderService.cancelMyOrder(41L, 501L, new OrderCancelRequest(null, "CHANGE_PRODUCT", null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReasonCode()).isNotNull();
        verify(voucherService).restoreVoucherForOrder(order);
        verify(notificationService).notifyOrderStatusChange(order, OrderStatus.PENDING, OrderStatus.CANCELLED);
    }

    @Test
    void cancelMyOrder_afterFiveMinutes_throwsWindowExpired() {
        User customer = User.builder().id(42L).build();
        Order order = Order.builder()
                .id(502L)
                .code("ORD-502")
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .createdAt(LocalDateTime.now().minusMinutes(6))
                .user(customer)
                .build();

        when(orderRepository.findById(502L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelMyOrder(
                42L,
                502L,
                new OrderCancelRequest(null, "CHANGE_PRODUCT", null)))
                .hasMessageContaining("quá thời gian cho phép hủy");

        verify(voucherService, never()).restoreVoucherForOrder(any());
        verify(notificationService, never()).notifyOrderStatusChange(any(), any(), eq(OrderStatus.CANCELLED));
    }

    @Test
    void getMyOrders_setsCanCancelBasedOnFiveMinuteWindow() {
        Order eligibleOrder = Order.builder()
                .id(601L)
                .code("ORD-601")
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(4))
                .build();
        Order expiredOrder = Order.builder()
                .id(602L)
                .code("ORD-602")
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(6))
                .build();

        when(orderRepository.findByUserIdOrderByCreatedAtDesc(43L)).thenReturn(List.of(eligibleOrder, expiredOrder));
        when(returnRequestRepository.findOrderIdsWithRequests(List.of(601L, 602L))).thenReturn(Set.of());

        List<OrderResponse> responses = orderService.getMyOrders(43L, null);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getCanCancel()).isTrue();
        assertThat(responses.get(1).getCanCancel()).isFalse();
    }

    @Test
    void getBranchOrderDetail_singleBranchVoucher_usesVoucherAdjustedFinalAmount() {
        Branch branch = Branch.builder().id(77L).name("Chi nhánh 77").build();
        Order order = Order.builder()
                .id(901L)
                .code("ORD-901")
                .discountAmount(new BigDecimal("50.00"))
                .build();
        SubOrder subOrder = SubOrder.builder()
                .id(301L)
                .order(order)
                .branch(branch)
                .subtotal(new BigDecimal("200.00"))
                .shippingFee(new BigDecimal("20.00"))
                .build();
        order.setSubOrders(List.of(subOrder));

        when(subOrderRepository.findByOrderIdAndBranchId(901L, 77L)).thenReturn(Optional.of(subOrder));

        BranchOrderResponse response = orderService.getBranchOrderDetail(77L, 901L);

        assertThat(response.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(response.getShippingFee()).isEqualByComparingTo("20.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getFinalAmount()).isEqualByComparingTo("170.00");
    }

    @Test
    void getBranchOrderDetail_multiBranchVoucher_allocatesDiscountBySubtotalWithRemainderOnLastSubOrder() {
        Branch branchOne = Branch.builder().id(81L).name("Chi nhánh 81").build();
        Branch branchTwo = Branch.builder().id(82L).name("Chi nhánh 82").build();
        Order order = Order.builder()
                .id(902L)
                .code("ORD-902")
                .discountAmount(new BigDecimal("100.00"))
                .build();
        SubOrder firstSubOrder = SubOrder.builder()
                .id(401L)
                .order(order)
                .branch(branchOne)
                .subtotal(new BigDecimal("100.00"))
                .shippingFee(new BigDecimal("10.00"))
                .build();
        SubOrder lastSubOrder = SubOrder.builder()
                .id(402L)
                .order(order)
                .branch(branchTwo)
                .subtotal(new BigDecimal("200.00"))
                .shippingFee(new BigDecimal("20.00"))
                .build();
        order.setSubOrders(List.of(lastSubOrder, firstSubOrder));

        when(subOrderRepository.findByOrderIdAndBranchId(902L, 81L)).thenReturn(Optional.of(firstSubOrder));
        when(subOrderRepository.findByOrderIdAndBranchId(902L, 82L)).thenReturn(Optional.of(lastSubOrder));

        BranchOrderResponse firstBranchResponse = orderService.getBranchOrderDetail(81L, 902L);
        BranchOrderResponse lastBranchResponse = orderService.getBranchOrderDetail(82L, 902L);

        assertThat(firstBranchResponse.getDiscountAmount()).isEqualByComparingTo("33.33");
        assertThat(firstBranchResponse.getFinalAmount()).isEqualByComparingTo("76.67");
        assertThat(lastBranchResponse.getDiscountAmount()).isEqualByComparingTo("66.67");
        assertThat(lastBranchResponse.getFinalAmount()).isEqualByComparingTo("153.33");
        assertThat(firstBranchResponse.getDiscountAmount().add(lastBranchResponse.getDiscountAmount()))
                .isEqualByComparingTo("100.00");
    }
}

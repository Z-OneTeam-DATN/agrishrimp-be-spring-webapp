package com.zone.agri.service;

import com.zone.agri.entity.AiKnowledgeReviewCase;
import com.zone.agri.entity.Notification;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.PurchaseRequest;
import com.zone.agri.entity.User;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.NotificationType;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.repository.NotificationRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho các notify* mới thêm vào NotificationService (đơn hàng, yêu cầu mua, case AI
 * Doctor, voucher). Mock toàn bộ dependency ngoài (repository, SimpMessagingTemplate,
 * WebPushService, EmailService) — không cần Spring context vì logic cần test (lọc trạng thái,
 * resolve-rồi-loop, không để lỗi email làm crash luồng chính) nằm gọn trong chính class này,
 * theo đúng pattern Mockito thuần đã dùng phổ biến nhất trong repo (vd.
 * OrderInventoryReservationServiceTest).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private WebPushService webPushService;
    @Mock
    private EmailService emailService;

    private NotificationService notificationService;

    private User customer;
    private Order order;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, orderRepository, userRepository, messagingTemplate,
                webPushService, emailService);

        customer = User.builder().id(1L).email("customer@example.com").fullName("Khach Hang").build();
        order = Order.builder().id(100L).code("ORD-100").user(customer).status(OrderStatus.CONFIRMED).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void notifyOrderStatusChange_allowlistedStatus_notifiesCustomerWithEmail() {
        notificationService.notifyOrderStatusChange(order, OrderStatus.PENDING, OrderStatus.CONFIRMED);

        verify(notificationRepository).save(argThat(n -> n.getNotificationType() == NotificationType.ORDER
                && n.getUser().getId().equals(1L)));
        verify(emailService).sendOrderStatusChangeEmail(order, OrderStatus.CONFIRMED);
    }

    @Test
    void notifyOrderStatusChange_nonAllowlistedStatus_doesNothing() {
        notificationService.notifyOrderStatusChange(order, OrderStatus.CONFIRMED, OrderStatus.READY_FOR_PICKUP);

        verify(notificationRepository, never()).save(any());
        verify(emailService, never()).sendOrderStatusChangeEmail(any(), any());
    }

    @Test
    void notifyOrderStatusChange_sameStatus_isNoOp() {
        notificationService.notifyOrderStatusChange(order, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyOrderStatusChange_awaitingReplenishment_notifiesAdminsNotCustomer() {
        User admin = User.builder().id(2L).email("admin@example.com").fullName("Admin Kho").build();
        when(userRepository.findUsersByPermissionCodeAndBranch("ORDER_UPDATE", null)).thenReturn(List.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        notificationService.notifyOrderStatusChange(order, OrderStatus.AWAITING_PAYMENT, OrderStatus.AWAITING_REPLENISHMENT);

        verify(notificationRepository).save(argThat(n -> n.getUser().getId().equals(2L)));
        verify(notificationRepository, never()).save(argThat(n -> n.getUser().getId().equals(1L)));
        verify(emailService).sendOrderReplenishmentAlertEmail("admin@example.com", "Admin Kho", order);
        verify(emailService, never()).sendOrderStatusChangeEmail(any(), any());
    }

    @Test
    void notifyOrderStatusChange_noAdminFound_doesNotThrow() {
        when(userRepository.findUsersByPermissionCodeAndBranch("ORDER_UPDATE", null)).thenReturn(List.of());

        assertThatCode(() -> notificationService.notifyOrderStatusChange(
                order, OrderStatus.AWAITING_PAYMENT, OrderStatus.AWAITING_REPLENISHMENT))
                .doesNotThrowAnyException();
    }

    @Test
    void notifyOrderStatusChange_emailThrows_doesNotPropagate_bellStillSaved() {
        doThrow(new RuntimeException("Resend khong ket noi duoc")).when(emailService)
                .sendOrderStatusChangeEmail(any(), any());

        assertThatCode(() -> notificationService.notifyOrderStatusChange(order, OrderStatus.PENDING, OrderStatus.CONFIRMED))
                .doesNotThrowAnyException();

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyOrderStatusChange_customerHasNoEmail_skipsEmailKeepsBell() {
        User phoneOnlyCustomer = User.builder().id(1L).phoneNumber("0900000000").fullName("Khach Hang").build();
        Order phoneOnlyOrder = Order.builder().id(101L).code("ORD-101").user(phoneOnlyCustomer)
                .status(OrderStatus.CONFIRMED).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(phoneOnlyCustomer));

        notificationService.notifyOrderStatusChange(phoneOnlyOrder, OrderStatus.PENDING, OrderStatus.CONFIRMED);

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService, never()).sendOrderStatusChangeEmail(any(), any());
    }

    @Test
    void notifyPurchaseRequestNeedsApproval_resolvesApproversAndNotifiesEach() {
        User approver = User.builder().id(3L).email("approver@example.com").fullName("Nguoi Duyet").build();
        when(userRepository.findUsersByPermissionCodeAndBranch("PURCHASE_REQUEST_APPROVE", null))
                .thenReturn(List.of(approver));
        when(userRepository.findById(3L)).thenReturn(Optional.of(approver));
        PurchaseRequest pr = PurchaseRequest.builder().id(5L).code("PR-5").build();

        notificationService.notifyPurchaseRequestNeedsApproval(pr);

        verify(notificationRepository).save(argThat(n -> n.getUser().getId().equals(3L)
                && n.getNotificationType() == NotificationType.SYSTEM));
        verify(emailService).sendPurchaseRequestApprovalNeededEmail("approver@example.com", "Nguoi Duyet", pr);
    }

    @Test
    void notifyPurchaseRequestNeedsApproval_noApprovers_doesNotThrow() {
        when(userRepository.findUsersByPermissionCodeAndBranch("PURCHASE_REQUEST_APPROVE", null))
                .thenReturn(List.of());
        PurchaseRequest pr = PurchaseRequest.builder().id(6L).code("PR-6").build();

        assertThatCode(() -> notificationService.notifyPurchaseRequestNeedsApproval(pr)).doesNotThrowAnyException();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyAgronomistsReviewCaseCreated_bellOnly_noEmail() {
        User agronomist = User.builder().id(4L).email("agronomist@example.com").fullName("Ky Su").build();
        when(userRepository.findUsersByPermissionCodeAndBranch("AI_CASE_REVIEW", null)).thenReturn(List.of(agronomist));
        when(userRepository.findById(4L)).thenReturn(Optional.of(agronomist));
        AiKnowledgeReviewCase reviewCase = AiKnowledgeReviewCase.builder().id(7L).questionText("Tom bi dom trang").build();

        notificationService.notifyAgronomistsReviewCaseCreated(reviewCase);

        verify(notificationRepository).save(argThat(n -> n.getUser().getId().equals(4L)
                && n.getNotificationType() == NotificationType.SYSTEM));
        verify(emailService, never()).sendOrderStatusChangeEmail(any(), any());
        verify(emailService, never()).sendOrderReplenishmentAlertEmail(any(), any(), any());
        verify(emailService, never()).sendPurchaseRequestApprovalNeededEmail(any(), any(), any());
    }

    @Test
    void notifyCustomerVoucherExpiringSoon_notifiesWithEmail() {
        Voucher voucher = Voucher.builder().id(8L).code("SALE10").build();

        notificationService.notifyCustomerVoucherExpiringSoon(customer, voucher, 3);

        verify(notificationRepository).save(argThat(n -> n.getUser().getId().equals(1L)));
        verify(emailService).sendVoucherExpiringSoonEmail("customer@example.com", "Khach Hang", voucher, 3);
    }
}

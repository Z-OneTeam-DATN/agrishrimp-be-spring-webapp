package com.zone.agri.service;

import com.zone.agri.dto.response.NotificationResponse;
import com.zone.agri.entity.AiKnowledgeReviewCase;
import com.zone.agri.entity.Notification;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.PurchaseRequest;
import com.zone.agri.entity.Review;
import com.zone.agri.entity.User;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.NotificationType;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.repository.NotificationRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final Set<OrderStatus> CUSTOMER_NOTIFIABLE_STATUSES = EnumSet.of(
            OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT, OrderStatus.AWAITING_REPLENISHMENT,
            OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.READY_FOR_PICKUP,
            OrderStatus.SHIPPING, OrderStatus.RECEIVED, OrderStatus.COMPLETED,
            OrderStatus.CANCELLED, OrderStatus.RETURNED);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebPushService webPushService;
    private final EmailService emailService;

    @Transactional
    public void sendNotification(Long userId, String title, String content,
                                  NotificationType type, Long referenceId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .content(content)
                .notificationType(type)
                .referenceId(referenceId)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = toResponse(saved);

        String userPrincipal = user.getEmail() != null ? user.getEmail() : user.getPhoneNumber();
        messagingTemplate.convertAndSendToUser(
                userPrincipal,
                "/queue/notifications",
                response
        );

        // Web Push (fire-and-forget)
        try {
            webPushService.sendPushToUser(userId, title, content, null);
        } catch (Exception e) {
            log.warn("[WebPush] Failed to send push for user {}: {}", userId, e.getMessage());
        }
    }

    // =========================================================
    // ĐƠN HÀNG — khách hàng nhận khi trạng thái đổi tới mốc quan trọng,
    // admin nhận khi đơn chờ nhập bù kho
    // =========================================================

    @Transactional
    public void notifyOrderPlaced(Order order) {
        if (order == null || order.getUser() == null) {
            return;
        }

        User customer = order.getUser();
        String title = "Đơn hàng " + order.getCode() + " đã được ghi nhận";
        String content = "Cảm ơn bạn đã đặt hàng. AgriShrimp sẽ xử lý đơn trong thời gian sớm nhất.";

        try {
            sendNotification(customer.getId(), title, content, NotificationType.ORDER, order.getId());
        } catch (Exception e) {
            log.warn("[Notify] Failed to send order-created notification for order {}: {}",
                    order.getId(), e.getMessage());
        }

        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            try {
                emailService.sendOrderPlacedEmail(order);
            } catch (Exception e) {
                log.warn("[Email] Failed to send order-created email for order {}: {}",
                        order.getId(), e.getMessage());
            }
        }

        if (order.getStatus() == OrderStatus.AWAITING_REPLENISHMENT) {
            notifyAdminsOrderNeedsReplenishment(order);
        }
    }

    @Transactional
    public void notifyOrderStatusChange(Order order, OrderStatus previousStatus, OrderStatus newStatus) {
        if (newStatus == null || newStatus == previousStatus) return;

        if (CUSTOMER_NOTIFIABLE_STATUSES.contains(newStatus)) {
            notifyCustomerOrderStatusChanged(order, newStatus);
        }
        if (newStatus == OrderStatus.AWAITING_REPLENISHMENT) {
            notifyAdminsOrderNeedsReplenishment(order);
        }
    }

    private void notifyCustomerOrderStatusChanged(Order order, OrderStatus newStatus) {
        User customer = order.getUser();
        if (customer == null) return;

        String title = "Đơn hàng " + order.getCode() + " đã cập nhật";
        String content = "Đơn hàng của bạn vừa chuyển sang trạng thái mới.";
        sendNotification(customer.getId(), title, content, NotificationType.ORDER, order.getId());

        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            try {
                emailService.sendOrderStatusChangeEmail(order, newStatus);
            } catch (Exception e) {
                log.warn("[Email] Failed to send order status email for order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    private void notifyAdminsOrderNeedsReplenishment(Order order) {
        List<User> admins = userRepository.findUsersByPermissionCodeAndBranch("ORDER_UPDATE", null);
        if (admins.isEmpty()) {
            log.warn("[Notify] No ORDER_UPDATE admin found to alert for order {}", order.getId());
            return;
        }

        String title = "Đơn hàng " + order.getCode() + " chờ nhập bù kho";
        String content = "Đơn hàng đang tạm dừng do thiếu hàng, cần xử lý yêu cầu nhập kho.";
        for (User admin : admins) {
            try {
                sendNotification(admin.getId(), title, content, NotificationType.ORDER, order.getId());
                if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                    emailService.sendOrderReplenishmentAlertEmail(admin.getEmail(), admin.getFullName(), order);
                }
            } catch (Exception e) {
                log.warn("[Notify] Failed to alert admin {} for order {}: {}", admin.getId(), order.getId(), e.getMessage());
            }
        }
    }

    // =========================================================
    // YÊU CẦU MUA HÀNG — admin có quyền duyệt nhận khi có phiếu chờ duyệt
    // =========================================================

    @Transactional
    public void notifyPurchaseRequestNeedsApproval(PurchaseRequest purchaseRequest) {
        List<User> approvers = userRepository.findUsersByPermissionCodeAndBranch("PURCHASE_REQUEST_APPROVE", null);
        if (approvers.isEmpty()) {
            log.warn("[Notify] No PURCHASE_REQUEST_APPROVE user found for request {}", purchaseRequest.getId());
            return;
        }

        String title = "Yêu cầu mua " + purchaseRequest.getCode() + " chờ duyệt";
        String content = "Có phiếu yêu cầu mua hàng mới đang chờ bạn duyệt.";
        for (User approver : approvers) {
            try {
                sendNotification(approver.getId(), title, content, NotificationType.SYSTEM, purchaseRequest.getId());
                if (approver.getEmail() != null && !approver.getEmail().isBlank()) {
                    emailService.sendPurchaseRequestApprovalNeededEmail(approver.getEmail(), approver.getFullName(), purchaseRequest);
                }
            } catch (Exception e) {
                log.warn("[Notify] Failed to alert approver {} for purchase request {}: {}",
                        approver.getId(), purchaseRequest.getId(), e.getMessage());
            }
        }
    }

    // =========================================================
    // AI DOCTOR — kỹ sư nhận khi có case chưa khớp bệnh nào cần xem xét
    // =========================================================

    @Transactional
    public void notifyAgronomistsReviewCaseCreated(AiKnowledgeReviewCase reviewCase) {
        List<User> agronomists = userRepository.findUsersByPermissionCodeAndBranch("AI_CASE_REVIEW", null);
        if (agronomists.isEmpty()) {
            log.warn("[Notify] No AI_CASE_REVIEW user found for review case {}", reviewCase.getId());
            return;
        }

        String title = "Case AI Doctor mới cần xem xét";
        String content = reviewCase.getQuestionText() != null
                ? reviewCase.getQuestionText()
                : "Có câu hỏi chưa khớp bệnh nào trong tri thức đã duyệt.";
        for (User agronomist : agronomists) {
            try {
                sendNotification(agronomist.getId(), title, content, NotificationType.SYSTEM, reviewCase.getId());
            } catch (Exception e) {
                log.warn("[Notify] Failed to alert agronomist {} for review case {}: {}",
                        agronomist.getId(), reviewCase.getId(), e.getMessage());
            }
        }
    }

    // =========================================================
    // VOUCHER — khách hàng nhận khi voucher đã lưu sắp hết hạn
    // =========================================================

    @Transactional
    public void notifyCustomerVoucherExpiringSoon(User user, Voucher voucher, int daysLeft) {
        if (user == null) return;

        String title = "Voucher " + voucher.getCode() + " sắp hết hạn";
        String content = "Voucher bạn đã lưu sẽ hết hạn trong " + daysLeft + " ngày nữa.";
        sendNotification(user.getId(), title, content, NotificationType.SYSTEM, voucher.getId());

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            try {
                emailService.sendVoucherExpiringSoonEmail(user.getEmail(), user.getFullName(), voucher, daysLeft);
            } catch (Exception e) {
                log.warn("[Email] Failed to send voucher-expiring email for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    // =========================================================
    // ĐÁNH GIÁ SẢN PHẨM — admin quản lý sản phẩm nhận khi có review mới (thông tin, không chặn hiển thị)
    // =========================================================

    @Transactional
    public void notifyAdminsNewReviewPosted(Review review) {
        List<User> productAdmins = userRepository.findUsersByPermissionCodeAndBranch("PRODUCT_UPDATE", null);
        if (productAdmins.isEmpty()) return;

        String productName = review.getProduct() != null ? review.getProduct().getName() : "sản phẩm";
        String title = "Đánh giá mới cho " + productName;
        String content = review.getComment() != null && !review.getComment().isBlank()
                ? review.getComment()
                : "Có đánh giá mới (" + review.getRating() + " sao) vừa được đăng.";
        for (User admin : productAdmins) {
            try {
                sendNotification(admin.getId(), title, content, NotificationType.SYSTEM, review.getId());
            } catch (Exception e) {
                log.warn("[Notify] Failed to alert admin {} for review {}: {}", admin.getId(), review.getId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(userId)) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        });
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .content(n.getContent())
                .notificationType(n.getNotificationType())
                .isRead(n.getIsRead())
                .referenceId(n.getReferenceId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

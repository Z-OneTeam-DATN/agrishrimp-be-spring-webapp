package com.zone.agri.controller;

import com.zone.agri.dto.response.NotificationResponse;
import com.zone.agri.entity.User;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.NotificationService;
import com.zone.agri.service.WebPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notifications management")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final WebPushService webPushService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Bạn cần đăng nhập");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Không tìm thấy người dùng"));
    }

    @Operation(summary = "Lấy danh sách thông báo của người dùng")
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        User user = getCurrentUser();
        return ResponseEntity.ok(notificationService.getMyNotifications(user.getId(), page, size));
    }

    @Operation(summary = "Lấy số lượng thông báo chưa đọc")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        User user = getCurrentUser();
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(user.getId())));
    }

    @Operation(summary = "Đánh dấu đọc tất cả thông báo")
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        User user = getCurrentUser();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Đánh dấu đọc một thông báo")
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        User user = getCurrentUser();
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Lấy VAPID public key cho Web Push")
    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", webPushService.getVapidPublicKey()));
    }

    @Operation(summary = "Đăng ký Web Push subscription")
    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@RequestBody Map<String, String> body) {
        User user = getCurrentUser();
        webPushService.subscribe(user.getId(), body.get("endpoint"), body.get("p256dh"), body.get("auth"));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Hủy đăng ký Web Push subscription")
    @DeleteMapping("/subscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody Map<String, String> body) {
        User user = getCurrentUser();
        webPushService.unsubscribe(user.getId(), body.get("endpoint"));
        return ResponseEntity.noContent().build();
    }
}

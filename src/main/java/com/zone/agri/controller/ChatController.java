package com.zone.agri.controller;

import com.zone.agri.dto.request.ChatMessageRequest;
import com.zone.agri.dto.request.PinProductRequest;
import com.zone.agri.dto.response.ChatMessageResponse;
import com.zone.agri.dto.response.ConversationResponse;
import com.zone.agri.entity.User;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Customer - Shop chat management")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Bạn cần đăng nhập");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Không tìm thấy người dùng"));
    }

    @Operation(summary = "Lấy hoặc tạo conversation của người dùng hiện tại")
    @GetMapping("/my-conversation")
    public ResponseEntity<ConversationResponse> getMyConversation() {
        User user = getCurrentUser();
        return ResponseEntity.ok(chatService.getOrCreateConversation(user.getId()));
    }

    @Operation(summary = "Lấy danh sách tất cả conversations (dành cho shop/admin)")
    @GetMapping("/conversations")
    @RequirePermission({"CHAT_VIEW", "CUSTOMER_ADVISOR_USE"})
    public ResponseEntity<Page<ConversationResponse>> getAllConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(chatService.getAllConversations(page, size));
    }

    @Operation(summary = "Lấy lịch sử tin nhắn của conversation")
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable Long id) {
        User user = getCurrentUser();
        chatService.markAsRead(id, user.getId());
        return ResponseEntity.ok(chatService.getMessages(id));
    }

    @Operation(summary = "Gửi tin nhắn qua REST (fallback nếu WebSocket không khả dụng)")
    @PostMapping("/conversations/{id}/send")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable Long id,
            @RequestBody ChatMessageRequest request) {
        User user = getCurrentUser();
        request.setConversationId(id);
        return ResponseEntity.ok(chatService.sendMessage(user.getId(), request));
    }

    @Operation(summary = "Người bán ghim sản phẩm vào conversation")
    @PostMapping("/conversations/{id}/pin-product")
    @RequirePermission("CHAT_MANAGE")
    public ResponseEntity<ChatMessageResponse> pinProduct(
            @PathVariable Long id,
            @RequestBody PinProductRequest request) {
        User user = getCurrentUser();
        return ResponseEntity.ok(chatService.pinProduct(user.getId(), id, request));
    }

    @Operation(summary = "Phân công nhân viên phụ trách conversation")
    @PutMapping("/conversations/{id}/assign")
    @RequirePermission("CHAT_MANAGE")
    public ResponseEntity<ConversationResponse> assignStaff(
            @PathVariable Long id,
            @RequestParam(required = false) Long staffId) {
        return ResponseEntity.ok(chatService.assignStaff(id, staffId));
    }

    @Operation(summary = "Gửi ảnh vào conversation")
    @PostMapping("/conversations/{id}/send-image")
    public ResponseEntity<ChatMessageResponse> sendImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        User user = getCurrentUser();
        return ResponseEntity.ok(chatService.sendImageMessage(user.getId(), id, file));
    }

    @Operation(summary = "Đánh dấu đã đọc tất cả tin nhắn trong conversation")
    @PutMapping("/conversations/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        User user = getCurrentUser();
        chatService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    // ===== WebSocket handlers =====

    @MessageMapping("/chat.send")
    public void handleSendMessage(@Payload ChatMessageRequest request, Principal principal) {
        if (principal == null) return;
        User sender = userRepository.findByEmail(principal.getName()).orElse(null);
        if (sender == null) return;
        chatService.sendMessage(sender.getId(), request);
    }

    @MessageMapping("/chat.read")
    public void handleMarkRead(@Payload Long conversationId, Principal principal) {
        if (principal == null) return;
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) return;
        chatService.markAsRead(conversationId, user.getId());
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) return;
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) return;
        Object convIdObj = payload.get("conversationId");
        if (convIdObj == null) return;
        Long convId = ((Number) convIdObj).longValue();
        chatService.broadcastTyping(user.getId(), convId);
    }
}

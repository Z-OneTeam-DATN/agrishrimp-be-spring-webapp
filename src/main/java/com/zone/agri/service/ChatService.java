package com.zone.agri.service;

import com.zone.agri.dto.request.ChatMessageRequest;
import com.zone.agri.dto.request.PinProductRequest;
import com.zone.agri.dto.response.ChatMessageResponse;
import com.zone.agri.dto.response.ConversationResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.ConversationStatus;
import com.zone.agri.entity.enums.MessageType;
import com.zone.agri.entity.enums.NotificationType;
import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.ai.AiChatResponse;
import com.zone.agri.dto.request.ai.AiDoctorChatRequest;
import com.zone.agri.repository.*;
import com.zone.agri.service.ai.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final CloudinaryService cloudinaryService;
    private final AiKnowledgeService aiKnowledgeService;

    @Transactional
    public ConversationResponse getOrCreateConversation(Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Conversation conv = conversationRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    Conversation newConv = Conversation.builder()
                            .customer(customer)
                            .status(ConversationStatus.OPEN)
                            .unreadByShop(0)
                            .unreadByCustomer(0)
                            .build();
                    return conversationRepository.save(newConv);
                });
        return toConversationResponse(conv);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getAllConversations(int page, int size) {
        return conversationRepository.findAllOrderByLastMessageDesc(PageRequest.of(page, size))
                .map(this::toConversationResponse);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationById(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
        return toConversationResponse(conv);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long conversationId) {
        return chatMessageRepository.findByConversationId(conversationId)
                .stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long senderId, ChatMessageRequest request) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Conversation conv = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        ChatMessage msg = ChatMessage.builder()
                .conversation(conv)
                .sender(sender)
                .content(request.getContent())
                .messageType(request.getMessageType() != null ? request.getMessageType() : MessageType.TEXT)
                .isRead(false)
                .build();

        msg = chatMessageRepository.save(msg);

        conv.setLastMessage(request.getContent());
        conv.setLastMessageAt(LocalDateTime.now());
        conv.setLastSenderId(senderId);
        conv.setStatus(ConversationStatus.OPEN);
        Long customerId = conv.getCustomer().getId();
        boolean senderIsCustomer = senderId.equals(customerId);
        if (senderIsCustomer) {
            conv.setUnreadByShop(conv.getUnreadByShop() + 1);
        } else {
            conv.setUnreadByCustomer(conv.getUnreadByCustomer() + 1);
        }
        conversationRepository.save(conv);

        ChatMessageResponse response = toMessageResponse(msg);

        String customerPrincipal = conv.getCustomer().getEmail() != null
                ? conv.getCustomer().getEmail()
                : conv.getCustomer().getPhoneNumber();

        // Push message via WebSocket to both parties
        messagingTemplate.convertAndSendToUser(
                customerPrincipal,
                "/queue/messages",
                response
        );

        // Notify the other party
        if (senderIsCustomer) {
            messagingTemplate.convertAndSend("/topic/shop-messages", response);
            // AI auto-reply disabled for customer support chat
        } else {
            notificationService.sendNotification(
                    customerId,
                    "Bạn có tin nhắn mới từ shop",
                    sender.getFullName() + ": " + request.getContent(),
                    NotificationType.CHAT,
                    conv.getId()
            );
        }

        return response;
    }

    @Transactional
    public ChatMessageResponse pinProduct(Long staffId, Long conversationId, PinProductRequest request) {
        User sender = userRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        String content = request.getMessage() != null ? request.getMessage()
                : "Sản phẩm được ghim: " + product.getName();

        ChatMessage msg = ChatMessage.builder()
                .conversation(conv)
                .sender(sender)
                .content(content)
                .messageType(MessageType.PINNED_PRODUCT)
                .pinnedProduct(product)
                .isRead(false)
                .build();

        msg = chatMessageRepository.save(msg);

        conv.setLastMessage("[Sản phẩm ghim] " + product.getName());
        conv.setLastMessageAt(LocalDateTime.now());
        conv.setLastSenderId(staffId);
        conv.setUnreadByCustomer(conv.getUnreadByCustomer() + 1);
        conv.setStatus(ConversationStatus.OPEN);
        conversationRepository.save(conv);

        ChatMessageResponse response = toMessageResponse(msg);

        Long customerId = conv.getCustomer().getId();
        String customerPrincipalPin = conv.getCustomer().getEmail() != null
                ? conv.getCustomer().getEmail()
                : conv.getCustomer().getPhoneNumber();
        messagingTemplate.convertAndSendToUser(customerPrincipalPin, "/queue/messages", response);

        notificationService.sendNotification(
                customerId,
                "Shop đã ghim sản phẩm cho bạn",
                product.getName(),
                NotificationType.CHAT,
                conv.getId()
        );

        return response;
    }

    @Transactional
    public ChatMessageResponse sendImageMessage(Long senderId, Long conversationId, MultipartFile file) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        String imageUrl = cloudinaryService.upload(file, "chat").secureUrl();

        ChatMessage msg = ChatMessage.builder()
                .conversation(conv)
                .sender(sender)
                .content(null)
                .messageType(MessageType.IMAGE)
                .imageUrl(imageUrl)
                .isRead(false)
                .build();

        msg = chatMessageRepository.save(msg);

        conv.setLastMessage("[Hình ảnh]");
        conv.setLastMessageAt(LocalDateTime.now());
        conv.setLastSenderId(senderId);
        conv.setStatus(ConversationStatus.OPEN);
        Long customerId = conv.getCustomer().getId();
        boolean senderIsCustomer = senderId.equals(customerId);
        if (senderIsCustomer) {
            conv.setUnreadByShop(conv.getUnreadByShop() + 1);
        } else {
            conv.setUnreadByCustomer(conv.getUnreadByCustomer() + 1);
        }
        conversationRepository.save(conv);

        ChatMessageResponse response = toMessageResponse(msg);

        String customerPrincipalImg = conv.getCustomer().getEmail() != null
                ? conv.getCustomer().getEmail()
                : conv.getCustomer().getPhoneNumber();
        messagingTemplate.convertAndSendToUser(customerPrincipalImg, "/queue/messages", response);
        if (senderIsCustomer) {
            messagingTemplate.convertAndSend("/topic/shop-messages", response);
        } else {
            notificationService.sendNotification(customerId, "Shop đã gửi ảnh", "[Hình ảnh]",
                    NotificationType.CHAT, conv.getId());
        }

        return response;
    }

    @Transactional
    public void markAsRead(Long conversationId, Long readerId) {
        chatMessageRepository.markAsReadByConversationAndNotSender(conversationId, readerId);
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            if (readerId.equals(conv.getCustomer().getId())) {
                conv.setUnreadByCustomer(0);
            } else {
                // Admin/Staff read: set unreadByShop to 0, then notify customer
                conv.setUnreadByShop(0);
                conversationRepository.save(conv);

                // Send READ_RECEIPT event to customer so their tick turns to double-check
                String customerPrincipal = conv.getCustomer().getEmail() != null
                        ? conv.getCustomer().getEmail()
                        : conv.getCustomer().getPhoneNumber();
                java.util.Map<String, Object> readEvent = new java.util.HashMap<>();
                readEvent.put("type", "READ_RECEIPT");
                readEvent.put("conversationId", conversationId);
                messagingTemplate.convertAndSendToUser(customerPrincipal, "/queue/messages", readEvent);
                return;
            }
            conversationRepository.save(conv);
        });
    }

    private void sendBotAutoReply(Long convId, String customerMessage) {
        try {
            User botUser = userRepository.findByEmail("bot@agrishrimp.vn").orElse(null);
            if (botUser == null) return;

            AiChatResponse aiResp = aiKnowledgeService.answerChat(
                    AiDoctorChatRequest.builder()
                            .message(customerMessage)
                            .sessionId("chat_" + convId)
                            .build(),
                    null,
                    "CUSTOMER_CHAT_AUTO_REPLY",
                    false);

            String reply = aiResp != null && aiResp.isSuccess() ? aiResp.getReply() : null;
            if (reply == null || reply.isBlank()) return;

            Conversation conv = conversationRepository.findById(convId).orElse(null);
            if (conv == null) return;

            ChatMessage botMsg = ChatMessage.builder()
                    .conversation(conv)
                    .sender(botUser)
                    .content(reply)
                    .messageType(MessageType.TEXT)
                    .isRead(false)
                    .build();
            botMsg = chatMessageRepository.save(botMsg);

            conv.setLastMessage(reply);
            conv.setLastMessageAt(LocalDateTime.now());
            conv.setUnreadByCustomer(conv.getUnreadByCustomer() + 1);
            conversationRepository.save(conv);

            ChatMessageResponse botResponse = toMessageResponse(botMsg);
            String customerPrincipalBot = conv.getCustomer().getEmail() != null
                    ? conv.getCustomer().getEmail()
                    : conv.getCustomer().getPhoneNumber();
            messagingTemplate.convertAndSendToUser(customerPrincipalBot, "/queue/messages", botResponse);
            messagingTemplate.convertAndSend("/topic/shop-messages", botResponse);
        } catch (Exception e) {
            log.warn("[ChatBot] Auto-reply failed for conv {}: {}", convId, e.getMessage());
        }
    }

    public void broadcastTyping(Long senderId, Long conversationId) {
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            Map<String, Object> event = Map.of("conversationId", conversationId, "senderId", senderId);
            Long customerId = conv.getCustomer().getId();
            if (senderId.equals(customerId)) {
                messagingTemplate.convertAndSend("/topic/shop-typing", event);
            } else {
                String customerPrincipalTyping = conv.getCustomer().getEmail() != null
                        ? conv.getCustomer().getEmail()
                        : conv.getCustomer().getPhoneNumber();
                messagingTemplate.convertAndSendToUser(customerPrincipalTyping, "/queue/typing", event);
            }
        });
    }

    @Transactional
    public ConversationResponse assignStaff(Long conversationId, Long staffId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        User staff = staffId != null
                ? userRepository.findById(staffId).orElseThrow(() -> new RuntimeException("Staff not found"))
                : null;
        conv.setAssignedStaff(staff);
        return toConversationResponse(conversationRepository.save(conv));
    }

    @Transactional
    public ConversationResponse updateStatus(Long conversationId, com.zone.agri.entity.enums.ConversationStatus status) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        conv.setStatus(status);
        return toConversationResponse(conversationRepository.save(conv));
    }

    @Transactional
    public ConversationResponse markAsUnread(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        conv.setUnreadByShop(1);
        return toConversationResponse(conversationRepository.save(conv));
    }

    private ConversationResponse toConversationResponse(Conversation c) {
        User customer = c.getCustomer();
        User assigned = c.getAssignedStaff();
        return ConversationResponse.builder()
                .id(c.getId())
                .customerId(customer.getId())
                .customerName(customer.getFullName())
                .customerAvatar(customer.getAvatarUrl())
                .status(c.getStatus())
                .lastMessage(c.getLastMessage())
                .lastMessageAt(c.getLastMessageAt())
                .lastSenderId(c.getLastSenderId())
                .unreadByShop(c.getUnreadByShop())
                .unreadByCustomer(c.getUnreadByCustomer())
                .assignedStaffId(assigned != null ? assigned.getId() : null)
                .assignedStaffName(assigned != null ? assigned.getFullName() : null)
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage m) {
        ChatMessageResponse.ChatMessageResponseBuilder builder = ChatMessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderId(m.getSender().getId())
                .senderName(m.getSender().getFullName())
                .senderAvatar(m.getSender().getAvatarUrl())
                .content(m.getContent())
                .messageType(m.getMessageType())
                .imageUrl(m.getImageUrl())
                .isRead(m.getIsRead())
                .createdAt(m.getCreatedAt());

        if (m.getPinnedProduct() != null) {
            Product p = m.getPinnedProduct();
            String imageUrl = null;
            if (p.getProductImages() != null && !p.getProductImages().isEmpty()) {
                imageUrl = firstImageUrl(p.getProductImages().iterator().next().getImageUrl());
            } else if (p.getVariants() != null && !p.getVariants().isEmpty()) {
                imageUrl = p.getVariants().stream()
                        .map(variant -> firstImageUrl(variant.getImageUrl()))
                        .filter(url -> url != null && !url.isBlank())
                        .findFirst()
                        .orElse(null);
            }
            builder.pinnedProduct(ChatMessageResponse.PinnedProductInfo.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .slug(p.getSlug())
                    .imageUrl(imageUrl)
                    .build());
        }

        return builder.build();
    }

    private String firstImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        for (String candidate : imageUrl.split(",")) {
            String trimmed = candidate.trim();
            if (!trimmed.isBlank()
                    && !"null".equalsIgnoreCase(trimmed)
                    && !"undefined".equalsIgnoreCase(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }

    // Map of conversationId -> Map of userId -> username
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.ConcurrentHashMap<Long, String>> activeViewers =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void updateViewingStatus(Long userId, String username, Long conversationId, String status) {
        if ("JOIN".equals(status)) {
            activeViewers.computeIfAbsent(conversationId, k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put(userId, username);
        } else if ("LEAVE".equals(status)) {
            java.util.concurrent.ConcurrentHashMap<Long, String> viewers = activeViewers.get(conversationId);
            if (viewers != null) {
                viewers.remove(userId);
                if (viewers.isEmpty()) {
                    activeViewers.remove(conversationId);
                }
            }
        }
        broadcastViewers(conversationId);
    }

    public void broadcastViewers(Long conversationId) {
        java.util.concurrent.ConcurrentHashMap<Long, String> viewersMap = activeViewers.get(conversationId);
        List<Map<String, Object>> viewersList = new java.util.ArrayList<>();
        if (viewersMap != null) {
            for (Map.Entry<Long, String> entry : viewersMap.entrySet()) {
                viewersList.add(Map.of("userId", entry.getKey(), "username", entry.getValue()));
            }
        }
        
        Map<String, Object> event = Map.of(
                "conversationId", conversationId,
                "viewers", viewersList
        );
        messagingTemplate.convertAndSend("/topic/shop-viewers", event);
    }

    public void removeUserFromAllConversations(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            Long userId = user.getId();
            for (Map.Entry<Long, java.util.concurrent.ConcurrentHashMap<Long, String>> entry : activeViewers.entrySet()) {
                if (entry.getValue().containsKey(userId)) {
                    entry.getValue().remove(userId);
                    broadcastViewers(entry.getKey());
                }
            }
        });
    }
}

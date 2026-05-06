package com.zone.agri.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.entity.PushSubscription;
import com.zone.agri.entity.User;
import com.zone.agri.repository.PushSubscriptionRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebPushService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${vapid.subject:mailto:admin@agrishrimp.vn}")
    private String vapidSubject;

    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        pushSubscriptionRepository.findByUserIdAndEndpoint(userId, endpoint)
                .ifPresent(s -> pushSubscriptionRepository.delete(s));
        PushSubscription sub = PushSubscription.builder()
                .user(user)
                .endpoint(endpoint)
                .p256dh(p256dh)
                .auth(auth)
                .build();
        pushSubscriptionRepository.save(sub);
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        pushSubscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    public void sendPushToUser(Long userId, String title, String body, String url) {
        if (vapidPublicKey == null || vapidPublicKey.isBlank() || vapidPrivateKey == null || vapidPrivateKey.isBlank()) {
            return;
        }
        List<PushSubscription> subs = pushSubscriptionRepository.findByUserId(userId);
        if (subs.isEmpty()) return;

        try {
            PushService pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "url", url != null ? url : "/"
            ));
            for (PushSubscription sub : subs) {
                try {
                    Notification notification = new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
                    pushService.send(notification);
                } catch (Exception e) {
                    log.warn("[WebPush] Failed to send to sub {}: {}", sub.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[WebPush] sendPushToUser failed: {}", e.getMessage());
        }
    }

    public String getVapidPublicKey() {
        return vapidPublicKey != null ? vapidPublicKey : "";
    }
}

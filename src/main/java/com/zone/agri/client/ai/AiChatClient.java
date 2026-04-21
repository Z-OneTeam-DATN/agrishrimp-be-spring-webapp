package com.zone.agri.client.ai;

import com.zone.agri.dto.miniapp.ai.AiChatRequest;
import com.zone.agri.dto.miniapp.ai.AiChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
@Slf4j
public class AiChatClient {

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.chat-path:/ai/chat}")
    private String chatPath;

    private final RestTemplate restTemplate;

    public AiChatClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    public AiChatResponse chat(AiChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AiChatRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<AiChatResponse> response = restTemplate.exchange(
                    baseUrl + chatPath,
                    HttpMethod.POST,
                    entity,
                    AiChatResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("[AI-Chat] Lỗi gọi AI chat: {}", e.getMessage());
            throw new RuntimeException("AI service lỗi khi xử lý chat: " + e.getMessage());
        }
    }
}

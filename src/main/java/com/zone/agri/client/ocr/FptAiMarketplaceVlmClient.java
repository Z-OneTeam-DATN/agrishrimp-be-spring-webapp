package com.zone.agri.client.ocr;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.zone.agri.exception.BadRequestException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FptAiMarketplaceVlmClient {

    private static final String SYSTEM_PROMPT = """
            You are an OCR extraction assistant for Vietnamese citizen identification cards.
            Read the image carefully and extract only information visible on the FRONT side of a Vietnamese CCCD or CMND card.
            Return only one compact JSON object with exactly these keys:
            citizenId, fullName, dateOfBirth, gender, addressDetail, homeTown, nationality, cardType, confidence, isFrontSide, notes.
            Rules:
            - Use null when a field is not visible or is uncertain.
            - gender must be one of: MALE, FEMALE, OTHER, or null.
            - confidence must be a number from 0 to 100.
            - isFrontSide must be true only when the image is clearly the front side of the card.
            - Do not include markdown, code fences, or any explanation outside the JSON object.
            """;

    private static final String USER_PROMPT = """
            Trich xuat thong tin tu anh CCCD/CMT Viet Nam nay.
            Neu day khong phai mat truoc cua the hoac khong du ro de doc, hay tra ve JSON voi isFrontSide=false va notes giai thich ngan gon.
            """;

    @Value("${fpt.ai.vlm.base-url:https://mkp-api.fptcloud.com}")
    private String baseUrl;

    @Value("${fpt.ai.vlm.chat-completions-path:/chat/completions}")
    private String chatCompletionsPath;

    @Value("${fpt.ai.vlm.api-key:}")
    private String apiKey;

    @Value("${fpt.ai.vlm.model:}")
    private String model;

    private final RestTemplate restTemplate;

    public FptAiMarketplaceVlmClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String extractCitizenId(MultipartFile imageFile) {
        validateConfig();

        String contentType = resolveContentType(imageFile);
        byte[] imageBytes;
        try {
            imageBytes = imageFile.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Không thể đọc file ảnh CCCD. Vui lòng thử lại.");
        }

        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0);
        requestBody.put("stream", false);
        requestBody.put("messages", buildMessages(dataUrl));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey);
        headers.set("api-key", apiKey);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    joinUrl(baseUrl, chatCompletionsPath),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class);

            JsonNode payload = response.getBody();
            if (payload == null || payload.isNull()) {
                throw new BadRequestException("FPT AI Marketplace VLM trả về dữ liệu rỗng.");
            }

            String content = extractAssistantContent(payload);
            if (content == null || content.isBlank()) {
                log.warn("VLM payload khong co noi dung choices hop le: {}", payload);
                throw new BadRequestException("FPT AI Marketplace VLM chưa trả về nội dung hợp lệ.");
            }

            return content.trim();
        } catch (HttpStatusCodeException ex) {
            log.warn("FPT AI Marketplace VLM tra loi HTTP {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 401 || statusCode == 403) {
                throw new BadRequestException("FPT AI Marketplace từ chối xác thực. Vui lòng kiểm tra API key mới.");
            }
            if (statusCode == 429) {
                throw new BadRequestException("FPT AI Marketplace đã vượt hạn mức hoặc hết số dư. Vui lòng kiểm tra tài khoản.");
            }
            throw new BadRequestException("FPT AI Marketplace không xử lý được ảnh này. Vui lòng thử ảnh khác rõ hơn.");
        } catch (ResourceAccessException ex) {
            log.error("Khong ket noi duoc FPT AI Marketplace VLM", ex);
            throw new BadRequestException("Không thể kết nối FPT AI Marketplace lúc này. Vui lòng thử lại sau.");
        } catch (RestClientException ex) {
            log.error("Loi goi FPT AI Marketplace VLM", ex);
            throw new BadRequestException("Không thể xử lý ảnh CCCD qua FPT AI Marketplace lúc này. Vui lòng thử lại sau.");
        }
    }

    private void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("Chưa cấu hình FPT AI Marketplace API key cho backend.");
        }

        if (model == null || model.isBlank()) {
            throw new BadRequestException("Chưa cấu hình model FPT AI Marketplace cho OCR CCCD.");
        }
    }

    private List<Map<String, Object>> buildMessages(String dataUrl) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", SYSTEM_PROMPT));
        messages.add(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                        Map.of("type", "text", "text", USER_PROMPT))));
        return messages;
    }

    private String extractAssistantContent(JsonNode payload) {
        JsonNode contentNode = payload.path("choices").path(0).path("message").path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }

        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item.isTextual()) {
                    builder.append(item.asText());
                    continue;
                }

                JsonNode textNode = item.path("text");
                if (textNode.isTextual()) {
                    builder.append(textNode.asText());
                }
            }
            return builder.toString();
        }

        return null;
    }

    private String resolveContentType(MultipartFile imageFile) {
        String contentType = imageFile.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }

        String originalFilename = imageFile.getOriginalFilename();
        if (originalFilename == null) {
            return MediaType.IMAGE_JPEG_VALUE;
        }

        String normalized = originalFilename.toLowerCase();
        if (normalized.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }

        return MediaType.IMAGE_JPEG_VALUE;
    }

    private String joinUrl(String base, String path) {
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }
}

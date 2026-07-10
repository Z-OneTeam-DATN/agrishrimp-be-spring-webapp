package com.zone.agri.client.ai;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.zone.agri.dto.response.employee.OcrCccdResponse;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CccdOcrClient {

    @Value("${ocr.remote.enabled:false}")
    private boolean enabled;

    @Value("${ocr.remote.base-url:http://ai-visual-search:5001}")
    private String baseUrl;

    @Value("${ocr.remote.path:/ocr/cccd}")
    private String extractPath;

    private final RestTemplate restTemplate;

    public CccdOcrClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(25))
                .build();
    }

    public Optional<OcrCccdResponse> extract(MultipartFile imageFile) {
        if (!enabled) {
            return Optional.empty();
        }

        byte[] imageBytes;
        String filename;
        try {
            imageBytes = imageFile.getBytes();
            filename = imageFile.getOriginalFilename() != null
                    ? imageFile.getOriginalFilename()
                    : "cccd.jpg";
        } catch (IOException ex) {
            log.warn("[CCCD-OCR] Không thể đọc file upload: {}", ex.getMessage());
            return Optional.empty();
        }

        try {
            ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", fileResource);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<OcrCccdResponse> response = restTemplate.exchange(
                    baseUrl + extractPath,
                    HttpMethod.POST,
                    entity,
                    OcrCccdResponse.class);

            return Optional.ofNullable(response.getBody());
        } catch (Exception ex) {
            log.warn("[CCCD-OCR] Remote OCR không khả dụng, fallback local: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}

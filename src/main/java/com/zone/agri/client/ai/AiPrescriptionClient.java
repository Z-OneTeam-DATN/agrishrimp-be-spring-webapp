package com.zone.agri.client.ai;

import com.zone.agri.dto.miniapp.ai.AiPrescriptionRequest;
import com.zone.agri.dto.miniapp.ai.AiPrescriptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
@Slf4j
public class AiPrescriptionClient {

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.prescription-path:/ai/generate-prescription}")
    private String prescriptionPath;

    private final RestTemplate restTemplate;

    public AiPrescriptionClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .build();
    }

    public AiPrescriptionResponse generatePrescription(AiPrescriptionRequest request) {
        try {
            return callPrescription(request);
        } catch (Exception e) {
            log.warn("[AI-Prescription] lần 1 thất bại, retry: {}", e.getMessage());
            try {
                return callPrescription(request);
            } catch (Exception e2) {
                log.error("[AI-Prescription] lần 2 thất bại: {}", e2.getMessage());
                throw new RuntimeException("AI service lỗi khi tạo phác đồ điều trị: " + e2.getMessage());
            }
        }
    }

    private AiPrescriptionResponse callPrescription(AiPrescriptionRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AiPrescriptionRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<AiPrescriptionResponse> response = restTemplate.exchange(
                baseUrl + prescriptionPath,
                HttpMethod.POST,
                entity,
                AiPrescriptionResponse.class
        );
        return response.getBody();
    }
}

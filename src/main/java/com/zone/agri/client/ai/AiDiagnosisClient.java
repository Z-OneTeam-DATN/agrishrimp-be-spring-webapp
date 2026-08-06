package com.zone.agri.client.ai;

import com.zone.agri.dto.ai.AiPredictResponse;
import com.zone.agri.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@Component
@Slf4j
public class AiDiagnosisClient {

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.predict-path:/ai/predict-image}")
    private String predictPath;

    private final RestTemplate restTemplate;

    public AiDiagnosisClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Gọi AI predict-image với 1 retry tự động.
     * Bytes được đọc 1 lần trước retry loop để tránh stream exhaustion.
     */
    public AiPredictResponse predict(MultipartFile imageFile) {
        byte[] imageBytes;
        String filename;
        try {
            imageBytes = imageFile.getBytes();
            filename = imageFile.getOriginalFilename() != null
                    ? imageFile.getOriginalFilename() : "image.jpg";
        } catch (IOException e) {
            throw new BadRequestException("Không thể đọc file ảnh. Vui lòng thử lại.");
        }

        try {
            return callPredict(imageBytes, filename);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AI-Diagnosis] predict lần 1 thất bại, retry: {}", e.getMessage());
            try {
                return callPredict(imageBytes, filename);
            } catch (BadRequestException e2) {
                throw e2;
            } catch (Exception e2) {
                log.error("[AI-Diagnosis] predict lần 2 thất bại: {}", e2.getMessage());
                throw new RuntimeException("Không thể kết nối AI service để phân tích ảnh. Vui lòng thử lại sau.");
            }
        }
    }

    private AiPredictResponse callPredict(byte[] imageBytes, String filename) {
        ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<AiPredictResponse> response = restTemplate.exchange(
                baseUrl + predictPath,
                HttpMethod.POST,
                entity,
                AiPredictResponse.class
        );

        AiPredictResponse result = response.getBody();
        if (result == null || result.getStatus() == null) {
            log.warn("[AI-Diagnosis] predict trả về response null hoặc thiếu status: {}", result);
            throw new BadRequestException("AI service trả về kết quả không hợp lệ. Vui lòng thử lại.");
        }
        // Trả nguyên response — AiDoctorDiagnosisService sẽ phân nhánh theo status
        // (BLURRY, NON_SHRIMP trả success=false nhưng status hợp lệ)
        return result;
    }
}

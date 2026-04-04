package com.zone.agri.service;

import com.zone.agri.dto.response.employee.OcrCccdResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Service for OCR processing of CCCD images
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final RestTemplate restTemplate;

    @Value("${ocr.api.url:}")
    private String ocrApiUrl;

    @Value("${ocr.api.key:}")
    private String ocrApiKey;

    /**
     * Extract information from CCCD image using OCR API
     */
    public OcrCccdResponse extractCccdInfo(MultipartFile image) {
        try {
            log.info("Starting OCR processing for CCCD image");

            // Prepare request to OCR API (FPT/VNPT)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("api-key", ocrApiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", image.getResource());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ocrApiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOcrResponse(response.getBody());
            } else {
                throw new RuntimeException("OCR API returned error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error processing OCR for CCCD image", e);
            throw new RuntimeException("Không thể xử lý ảnh CCCD: " + e.getMessage());
        }
    }

    /**
     * Parse OCR API response and extract CCCD information
     */
    private OcrCccdResponse parseOcrResponse(Map<String, Object> ocrResult) {
        try {
            // This is a sample parsing logic - adjust based on actual OCR API response
            // format
            Map<String, Object> data = (Map<String, Object>) ocrResult.get("data");

            String fullName = extractField(data, "fullName", "name");
            String dateOfBirthStr = extractField(data, "dateOfBirth", "dob", "birthDate");
            String genderStr = extractField(data, "gender", "sex");
            String address = extractField(data, "address", "placeOfResidence", "address");
            String citizenId = extractField(data, "citizenId", "id", "cccd");

            // Parse date of birth
            LocalDate dateOfBirth = null;
            if (dateOfBirthStr != null) {
                try {
                    // Try different date formats
                    DateTimeFormatter[] formatters = {
                            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                            DateTimeFormatter.ofPattern("dd-MM-yyyy")
                    };

                    for (DateTimeFormatter formatter : formatters) {
                        try {
                            dateOfBirth = LocalDate.parse(dateOfBirthStr, formatter);
                            break;
                        } catch (Exception e) {
                            // Try next format
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not parse date of birth: {}", dateOfBirthStr);
                }
            }

            // Parse gender
            String gender = "OTHER";
            if (genderStr != null) {
                String lowerGender = genderStr.toLowerCase();
                if (lowerGender.contains("nam") || lowerGender.contains("male")) {
                    gender = "MALE";
                } else if (lowerGender.contains("nữ") || lowerGender.contains("female")) {
                    gender = "FEMALE";
                }
            }

            Double confidence = (Double) ocrResult.get("confidence");

            return OcrCccdResponse.builder()
                    .fullName(fullName)
                    .dateOfBirth(dateOfBirth != null ? dateOfBirth.toString() : null)
                    .gender(gender)
                    .address(address)
                    .citizenId(citizenId)
                    .confidence(confidence)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing OCR response", e);
            throw new RuntimeException("Không thể phân tích kết quả OCR");
        }
    }

    /**
     * Extract field value from OCR response with multiple possible keys
     */
    private String extractField(Map<String, Object> data, String... possibleKeys) {
        for (String key : possibleKeys) {
            Object value = data.get(key);
            if (value != null) {
                return value.toString().trim();
            }
        }
        return null;
    }
}
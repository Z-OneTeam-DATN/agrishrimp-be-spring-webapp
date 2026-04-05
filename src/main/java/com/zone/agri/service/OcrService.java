package com.zone.agri.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.zone.agri.dto.response.employee.OcrCccdResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        if (ocrApiUrl == null || ocrApiUrl.isBlank()) {
            throw new IllegalStateException("OCR API URL chưa được cấu hình. Vui lòng thiết lập OCR_API_URL.");
        }

        try {
            log.info("Starting OCR processing for CCCD image");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (ocrApiKey != null && !ocrApiKey.isBlank()) {
                headers.set("api-key", ocrApiKey);
            }

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new MultipartImageResource(image));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ocrApiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOcrResponse(response.getBody());
            }

            throw new RuntimeException("OCR API returned error: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Error processing OCR for CCCD image", e);
            throw new RuntimeException("Không thể xử lý ảnh CCCD: " + e.getMessage(), e);
        }
    }

    /**
     * Parse OCR API response and extract CCCD information
     */
    private OcrCccdResponse parseOcrResponse(Map<String, Object> ocrResult) {
        try {
            // This is a sample parsing logic - adjust based on actual OCR API response
            // format
            Map<String, Object> data = asMap(ocrResult.get("data"));
            if (data.isEmpty()) {
                data = asMap(ocrResult);
            }

            String fullName = extractField(data, "fullName", "name", "hoTen", "ten");
            String dateOfBirthStr = extractField(data, "dateOfBirth", "dob", "birthDate", "ngaySinh");
            String genderStr = extractField(data, "gender", "sex", "gioiTinh");
            String address = extractField(data, "address", "placeOfResidence", "address", "diaChi");
            String citizenId = extractField(data, "citizenId", "id", "cccd", "soCccd");

            LocalDate dateOfBirth = null;
            if (dateOfBirthStr != null) {
                DateTimeFormatter[] formatters = {
                        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                        DateTimeFormatter.ofPattern("dd-MM-yyyy")
                };

                for (DateTimeFormatter formatter : formatters) {
                    try {
                        dateOfBirth = LocalDate.parse(dateOfBirthStr, formatter);
                        break;
                    } catch (Exception ignored) {
                    }
                }
                if (dateOfBirth == null) {
                    log.warn("Could not parse date of birth: {}", dateOfBirthStr);
                }
            }

            String gender = "OTHER";
            if (genderStr != null) {
                String lowerGender = genderStr.toLowerCase();
                if (lowerGender.contains("nam") || lowerGender.contains("male")) {
                    gender = "MALE";
                } else if (lowerGender.contains("nữ") || lowerGender.contains("female") || lowerGender.contains("nu")) {
                    gender = "FEMALE";
                }
            }

            Double confidence = parseConfidence(ocrResult.get("confidence"));

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
            throw new RuntimeException("Không thể phân tích kết quả OCR: " + e.getMessage(), e);
        }
    }

    /**
     * Extract field value from OCR response with multiple possible keys
     */
    private String extractField(Map<String, Object> data, String... possibleKeys) {
        if (data == null) {
            return null;
        }

        for (String key : possibleKeys) {
            Object value = data.get(key);
            if (value != null) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return Map.of();
    }

    private Double parseConfidence(Object confidenceObj) {
        if (confidenceObj == null) {
            return null;
        }
        if (confidenceObj instanceof Number) {
            return ((Number) confidenceObj).doubleValue();
        }
        try {
            return Double.parseDouble(confidenceObj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class MultipartImageResource extends ByteArrayResource {
        private final String filename;

        public MultipartImageResource(MultipartFile multipartFile) {
            super(getBytesQuietly(multipartFile));
            this.filename = Objects.requireNonNullElse(multipartFile.getOriginalFilename(), "image.jpg");
        }

        @Override
        public String getFilename() {
            return this.filename;
        }

        @Override
        public String getDescription() {
            return "Multipart image resource [" + this.filename + "]";
        }

        private static byte[] getBytesQuietly(MultipartFile multipartFile) {
            try {
                return multipartFile.getBytes();
            } catch (Exception e) {
                throw new RuntimeException("Không đọc được nội dung ảnh CCCD", e);
            }
        }
    }
}
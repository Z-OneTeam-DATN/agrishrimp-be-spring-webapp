package com.zone.agri.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
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
     * Extract information from CCCD image using Google Vision API
     */
    public OcrCccdResponse extractCccdInfo(MultipartFile image) {
        if (ocrApiUrl == null || ocrApiUrl.isBlank()) {
            throw new IllegalStateException(
                    "OCR API URL chưa được cấu hình. Vui lòng thiết lập GOOGLE_VISION_API_KEY.");
        }

        if (ocrApiKey == null || ocrApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Google Vision API Key chưa được cấu hình. Vui lòng thiết lập GOOGLE_VISION_API_KEY.");
        }

        try {
            log.info("Starting OCR processing for CCCD image using Google Vision API");

            // Convert image to base64
            byte[] imageBytes = image.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Build Google Vision API request
            Map<String, Object> requestBody = buildGoogleVisionRequest(base64Image);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Call Google Vision API
            String apiUrlWithKey = ocrApiUrl + "?key=" + ocrApiKey;
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrlWithKey,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String extractedText = extractTextFromGoogleVisionResponse(response.getBody());
                return parseCccdText(extractedText);
            }

            throw new RuntimeException("Google Vision API returned error: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Error processing OCR for CCCD image", e);
            throw new RuntimeException("Không thể xử lý ảnh CCCD: " + e.getMessage(), e);
        }
    }

    /**
     * Build Google Vision API request body
     */
    private Map<String, Object> buildGoogleVisionRequest(String base64Image) {
        Map<String, Object> image = Map.of("content", base64Image);
        Map<String, Object> feature = Map.of("type", "TEXT_DETECTION");
        List<Map<String, Object>> features = List.of(feature);
        Map<String, Object> request = Map.of("image", image, "features", features);
        return Map.of("requests", List.of(request));
    }

    /**
     * Extract text from Google Vision API response
     */
    private String extractTextFromGoogleVisionResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> responses = asList(response.get("responses"));
            if (responses.isEmpty()) {
                return "";
            }

            Map<String, Object> firstResponse = responses.get(0);
            List<Map<String, Object>> textAnnotations = asList(firstResponse.get("textAnnotations"));

            if (textAnnotations.isEmpty()) {
                return "";
            }

            return (String) textAnnotations.get(0).get("description");
        } catch (Exception e) {
            log.warn("Failed to extract text from Google Vision response", e);
            return "";
        }
    }

    /**
     * Parse CCCD information from extracted text using regex
     */
    private OcrCccdResponse parseCccdText(String text) {
        try {
            if (text == null || text.isBlank()) {
                return new OcrCccdResponse("", null, "", "", "");
            }

            log.info("Extracted text from CCCD: {}", text);

            // Extract CCCD number (12 digits)
            String citizenId = extractByRegex(text, "\\b\\d{12}\\b");

            // Extract full name (Vietnamese names after "Họ và tên" or similar)
            String fullName = extractByRegex(text, "(?i)(?:họ\\s+và\\s+tên|full\\s+name)[:\\s]*([A-ZÀ-Ỹ\\s]+)", 1);

            // Extract date of birth
            String dateOfBirthStr = extractByRegex(text, "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})");

            // Extract gender
            String gender = "";
            if (text.toLowerCase().contains("nam")) {
                gender = "Nam";
            } else if (text.toLowerCase().contains("nữ") || text.toLowerCase().contains("nu")) {
                gender = "Nữ";
            }

            // Extract address (after "Địa chỉ" or similar)
            String address = extractByRegex(text, "(?i)(?:địa\\s+chỉ|address)[:\\s]*([^\\n]+)", 1);

            // Parse date
            LocalDate dateOfBirth = null;
            if (dateOfBirthStr != null) {
                DateTimeFormatter[] formatters = {
                        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                        DateTimeFormatter.ofPattern("d/M/yyyy"),
                        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                        DateTimeFormatter.ofPattern("d-M-yyyy")
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

            return new OcrCccdResponse(fullName, dateOfBirth, gender, address, citizenId);

        } catch (Exception e) {
            log.error("Error parsing CCCD text", e);
            return new OcrCccdResponse("", null, "", "", "");
        }
    }

    /**
     * Extract field value using regex pattern
     */
    private String extractByRegex(String text, String pattern) {
        return extractByRegex(text, pattern, 0);
    }

    /**
     * Extract field value using regex pattern with group index
     */
    private String extractByRegex(String text, String pattern, int groupIndex) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find() && m.groupCount() >= groupIndex) {
                String result = m.group(groupIndex).trim();
                return result.isEmpty() ? null : result;
            }
        } catch (Exception e) {
            log.warn("Regex extraction failed for pattern: {}", pattern, e);
        }
        return null;
    }

    /**
     * Safe cast to List
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object obj) {
        return obj instanceof List ? (List<Map<String, Object>>) obj : List.of();
    }

    /**
     * Safe cast to Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : Map.of();
    }
}
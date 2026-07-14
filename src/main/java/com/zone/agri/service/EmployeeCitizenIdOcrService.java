package com.zone.agri.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.client.ocr.FptAiMarketplaceVlmClient;
import com.zone.agri.dto.response.employee.EmployeeCitizenIdOcrResponse;
import com.zone.agri.entity.enums.Gender;
import com.zone.agri.exception.BadRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeCitizenIdOcrService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter[] SUPPORTED_DOB_FORMATS = {
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    private final FptAiMarketplaceVlmClient fptAiMarketplaceVlmClient;
    private final ObjectMapper objectMapper;

    public EmployeeCitizenIdOcrResponse extractCitizenIdInfo(MultipartFile image) {
        validateUpload(image);

        JsonNode cardData = parseModelOutput(fptAiMarketplaceVlmClient.extractCitizenId(image));
        if (cardData == null || cardData.isNull() || cardData.isMissingNode()) {
            throw new BadRequestException("FPT AI Marketplace chưa trả về dữ liệu CCCD hợp lệ.");
        }

        Boolean isFrontSide = readBoolean(cardData, "isFrontSide");
        if (Boolean.FALSE.equals(isFrontSide)) {
            throw new BadRequestException(firstNonBlank(
                    normalizeText(readText(cardData, "notes")),
                    "Ảnh tải lên không phải mặt trước CCCD hoặc chưa đủ rõ để trích xuất."));
        }

        String citizenId = normalizeCitizenId(readText(cardData, "citizenId"));
        String fullName = normalizeText(readText(cardData, "fullName"));
        String dateOfBirth = normalizeDate(readText(cardData, "dateOfBirth"));
        Gender gender = normalizeGender(readText(cardData, "gender"));
        String addressDetail = normalizeText(readText(cardData, "addressDetail"));
        String homeTown = normalizeText(readText(cardData, "homeTown"));
        String nationality = normalizeText(readText(cardData, "nationality"));
        String cardType = normalizeText(readText(cardData, "cardType"));
        Double confidence = readProbability(cardData, "confidence");

        if (isBlank(citizenId) && isBlank(fullName) && isBlank(dateOfBirth)) {
            throw new BadRequestException(
                    "Không đọc được thông tin chính từ ảnh CCCD. Hãy dùng ảnh mặt trước, đủ 4 góc và rõ nét.");
        }

        return EmployeeCitizenIdOcrResponse.builder()
                .citizenId(citizenId)
                .fullName(fullName)
                .dateOfBirth(dateOfBirth)
                .gender(gender)
                .addressDetail(addressDetail)
                .homeTown(homeTown)
                .nationality(nationality)
                .cardType(cardType)
                .confidence(confidence)
                .build();
    }

    private void validateUpload(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ảnh CCCD trước khi tải lên.");
        }

        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BadRequestException("Ảnh CCCD vượt quá 5MB. Vui lòng nén ảnh hoặc chọn ảnh khác.");
        }

        String contentType = image.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BadRequestException("File tải lên phải là ảnh hợp lệ.");
        }
    }

    private JsonNode parseModelOutput(String rawOutput) {
        String cleaned = stripCodeFences(rawOutput);
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(cleaned);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException(
                    "FPT AI Marketplace trả về định dạng không đúng JSON mong đợi. Vui lòng thử lại với ảnh rõ hơn.");
        }
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isTextual() || valueNode.isNumber() || valueNode.isBoolean()) {
            return valueNode.asText();
        }

        if (valueNode.isObject()) {
            if (valueNode.hasNonNull("value")) {
                return valueNode.get("value").asText();
            }
            if (valueNode.hasNonNull("text")) {
                return valueNode.get("text").asText();
            }
        }

        return null;
    }

    private Boolean readBoolean(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        }

        String normalized = normalizeText(valueNode.asText());
        if (normalized == null) {
            return null;
        }

        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> null;
        };
    }

    private Double readProbability(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        double rawValue;
        if (valueNode.isNumber()) {
            rawValue = valueNode.asDouble();
        } else {
            String rawText = normalizeText(valueNode.asText());
            if (rawText == null) {
                return null;
            }
            try {
                rawValue = Double.parseDouble(rawText);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        return rawValue <= 1 ? rawValue * 100 : rawValue;
    }

    private String normalizeCitizenId(String rawCitizenId) {
        String cleaned = normalizeText(rawCitizenId);
        if (cleaned == null) {
            return null;
        }

        String digitsOnly = cleaned.replaceAll("\\D+", "");
        return digitsOnly.isBlank() ? cleaned : digitsOnly;
    }

    private String normalizeDate(String rawDate) {
        String cleaned = normalizeText(rawDate);
        if (cleaned == null) {
            return null;
        }

        for (DateTimeFormatter formatter : SUPPORTED_DOB_FORMATS) {
            try {
                LocalDate parsedDate = LocalDate.parse(cleaned, formatter);
                return parsedDate.toString();
            } catch (DateTimeParseException ignored) {
            }
        }

        return cleaned;
    }

    private Gender normalizeGender(String rawGender) {
        String cleaned = normalizeText(rawGender);
        if (cleaned == null) {
            return null;
        }

        String ascii = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();

        if (ascii.equals("nam") || ascii.equals("male") || ascii.equals("m")) {
            return Gender.MALE;
        }

        if (ascii.equals("nu") || ascii.equals("female") || ascii.equals("f")) {
            return Gender.FEMALE;
        }

        if (ascii.equals("other") || ascii.equals("khac")) {
            return Gender.OTHER;
        }

        return null;
    }

    private String stripCodeFences(String rawOutput) {
        String cleaned = normalizeText(rawOutput);
        if (cleaned == null) {
            return null;
        }

        if (!cleaned.startsWith("```")) {
            return cleaned;
        }

        String withoutOpeningFence = cleaned.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
        return withoutOpeningFence.replaceFirst("\\s*```$", "").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

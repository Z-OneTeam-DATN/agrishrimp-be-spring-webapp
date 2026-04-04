package com.zone.agri.dto.response.employee;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for OCR CCCD result
 */
@Data
@Builder
public class OcrCccdResponse {
    private String fullName;
    private String dateOfBirth; // Format: YYYY-MM-DD
    private String gender; // MALE, FEMALE, OTHER
    private String address;
    private String citizenId; // Extracted from CCCD
    private Double confidence; // OCR confidence score
}
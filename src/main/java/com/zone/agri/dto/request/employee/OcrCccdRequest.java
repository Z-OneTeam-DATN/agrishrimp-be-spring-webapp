package com.zone.agri.dto.request.employee;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * Request DTO for OCR CCCD upload
 */
@Data
public class OcrCccdRequest {
    private MultipartFile image;
}
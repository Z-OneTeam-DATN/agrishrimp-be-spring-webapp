package com.zone.agri.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả parse từ output JSON có cấu trúc của Gemini (xem GeminiClarifyClient).
 * responseType: "QUESTION" hoặc "DECISION" — bất kỳ giá trị nào khác đều bị AiDoctorClarifyService
 * coi là không hợp lệ và escalate sang review case, không đoán mò.
 */
@Data
@NoArgsConstructor
public class AiClarifyLlmResult {

    private String responseType;
    private String questionText;
    private String diseaseCode;
}

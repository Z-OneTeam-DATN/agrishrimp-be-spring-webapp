package com.zone.agri.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả parse từ output JSON có cấu trúc của Gemini describeImage() (xem GeminiClarifyClient).
 * isShrimp=false là tín hiệu để AiDoctorDiagnosisService chặn kết quả YOLO có thể nhận nhầm ảnh
 * không phải tôm thành HEALTHY/DISEASE, trả về NON_SHRIMP thay vì tin YOLO mù quáng.
 *
 * @JsonProperty tường minh cho isShrimp vì Lombok sinh getter isShrimp() cho field boolean, Jackson
 * mặc định sẽ suy ra tên thuộc tính là "shrimp" (bỏ tiền tố "is") — dễ lệch với tên field JSON schema
 * đã khai ở GeminiClarifyClient nếu không ghim rõ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiImageNarrativeResult {

    @JsonProperty("isShrimp")
    private boolean isShrimp;

    private String description;
}

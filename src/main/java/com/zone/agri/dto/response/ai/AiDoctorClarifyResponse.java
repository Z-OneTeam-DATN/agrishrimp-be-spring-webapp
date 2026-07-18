package com.zone.agri.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Response cho POST /diagnosis/{id}/clarify.
 *
 * type:
 * - QUESTION   → message là câu hỏi tiếp theo, hiển thị như tin nhắn chat bình thường.
 * - DECISION   → message là lời dẫn ngắn, diagnosis chứa kết quả đầy đủ (đã lấy phác đồ từ DB đã duyệt).
 * - ESCALATED  → hết trần an toàn / không hỏi được nữa, đã tạo review case cho kỹ sư xử lý.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiDoctorClarifyResponse {

    private String diagnosisId;
    private String type;
    private String message;
    private AiDoctorDiagnosisResponse diagnosis;

    /** Chỉ để log/thống kê nội bộ — FE không được hiển thị như một bộ đếm cho nông dân. */
    private Integer turnsUsed;
}

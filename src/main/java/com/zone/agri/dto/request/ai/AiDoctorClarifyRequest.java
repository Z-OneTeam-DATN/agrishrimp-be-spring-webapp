package com.zone.agri.dto.request.ai;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body cho POST /diagnosis/{id}/clarify.
 * - Lượt gọi đầu tiên của một phiên: candidateDiseaseCodes bắt buộc (top-N mã bệnh YOLO đoán được),
 *   answer để trống/null.
 * - Các lượt sau: chỉ cần answer — candidateDiseaseCodes bị bỏ qua vì tập candidate đã khoá từ đầu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDoctorClarifyRequest {

    private String answer;
    private List<String> candidateDiseaseCodes;

    /**
     * Chỉ cần thiết ở lượt gọi đầu tiên cho KHÁCH VÃNG LAI — vì khách vãng lai không có
     * AiDoctorDiagnosisHistory để lấy lại imageUrl/initialSymptoms, FE phải tự gửi kèm những
     * gì đã có sẵn trong response /diagnosis ban đầu. Bị bỏ qua nếu server đã có history
     * (user đăng nhập) hoặc ở các lượt sau.
     */
    private String imageUrl;
    private String initialSymptoms;
}

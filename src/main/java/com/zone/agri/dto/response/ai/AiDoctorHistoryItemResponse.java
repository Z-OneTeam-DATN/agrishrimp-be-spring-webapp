package com.zone.agri.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Một item trong danh sách lịch sử chẩn đoán.
 * Chỉ chứa thông tin tóm tắt — FE dùng để render danh sách history.
 * Để lấy full detail, FE gọi GET /api/ai-doctor/diagnosis/{id}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiDoctorHistoryItemResponse {

    /** ID bản ghi trong DB — FE dùng để gọi GET /api/ai-doctor/diagnosis/{id} */
    private String diagnosisId;

    private LocalDateTime createdAt;

    /** URL ảnh chụp — null nếu chưa upload */
    private String imageUrl;

    /** Thông tin bệnh đã chẩn đoán */
    private DiseaseResponse disease;

    /** true = còn đang chờ hỏi-đáp AI xác nhận, disease ở trên chỉ là dự đoán chưa chốt */
    private Boolean needsClarification;
}

package com.zone.agri.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response cho luồng AI Doctor:
 * - POST /api/ai-doctor/diagnosis (createdAt = null, excluded by NON_NULL)
 * - GET  /api/ai-doctor/diagnosis/{id} (createdAt = thời điểm lưu history)
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiDoctorDiagnosisResponse {

    private String diagnosisId;

    /** HEALTHY | DISEASE — FE dùng để render UI phù hợp */
    private String status;

    /** URL ảnh chẩn đoán — Phase BE-5: upload lên Cloudinary */
    private String imageUrl;

    private DiseaseResponse disease;
    private List<TopPredictionResponse> topPredictions;
    private List<String> causes;
    private String signsSummary;
    private List<TreatmentStageResponse> treatmentStages;

    /** Purchase link — Phase BE-5: bổ sung */
    private String purchaseUrl;

    /**
     * Thời điểm lưu history.
     * Null với POST response (excluded by NON_NULL).
     * Set với GET /diagnosis/{id} response.
     */
    private LocalDateTime createdAt;
}

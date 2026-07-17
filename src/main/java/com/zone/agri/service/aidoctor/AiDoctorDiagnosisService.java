package com.zone.agri.service.aidoctor;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.client.ai.AiDiagnosisClient;
import com.zone.agri.dto.ai.AiPredictResponse;
import com.zone.agri.dto.ai.AiPredictionItem;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.Product;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
import com.zone.agri.service.ai.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestration chính cho luồng chẩn đoán AI Doctor:
 *  Bước 1 — POST /diagnosis:      Validate image → YOLO predict → lưu history → trả kết quả bệnh
 *  Bước 2 — POST /diagnosis/{id}/prescription: Gemini generate-prescription → cập nhật history → trả phác đồ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiDoctorDiagnosisService {

    private final CloudinaryService cloudinaryService;
    private final AiDiagnosisClient aiDiagnosisClient;
    private final AiDoctorProductSuggestionService productSuggestionService;
    private final AiDoctorDiagnosisHistoryService diagnosisHistoryService;
    private final AiDoctorDiagnosisHistoryRepository historyRepository;
    private final AiKnowledgeService aiKnowledgeService;

    private static final long MAX_IMAGE_SIZE_BYTES = 5_000_000L;
    private static final int MAX_SYMPTOMS_LENGTH = 500;

    public AiDoctorDiagnosisResponse diagnose(MultipartFile image, String userSymptoms, Long userId) {
        return diagnose(image, userSymptoms, userId, null);
    }

    public AiDoctorDiagnosisResponse diagnose(MultipartFile image, String userSymptoms, Long userId, String sessionId) {

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // 1. Validate image
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Vui lòng tải lên hình ảnh để chẩn đoán");
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BadRequestException("Ảnh quá lớn (tối đa 5MB). Vui lòng chọn ảnh nhỏ hơn.");
        }

        // Normalize userSymptoms
        String normalizedSymptoms = (userSymptoms != null) ? userSymptoms.trim() : "";
        if (normalizedSymptoms.length() > MAX_SYMPTOMS_LENGTH) {
            normalizedSymptoms = normalizedSymptoms.substring(0, MAX_SYMPTOMS_LENGTH);
        }

        log.info("[AiDoctor] traceId={} start: userId={}, file={}, symptomsLen={}",
                traceId, userId, image.getOriginalFilename(), normalizedSymptoms.length());

        // 2. AI predict-image (fail → toàn bộ request fail, không graceful)
        AiPredictResponse predictResponse = aiDiagnosisClient.predict(image);
        String aiStatus = predictResponse.getStatus();
        String diagnosisImageUrl = uploadAnnotatedImageGracefully(predictResponse, traceId);

        // 2a. Xử lý sớm các trạng thái đặc biệt từ AI
        if ("BLURRY".equals(aiStatus)) {
            log.info("[AiDoctor] traceId={} BLURRY image", traceId);
            throw new BadRequestException(
                    "Ảnh quá mờ hoặc chất lượng thấp. Vui lòng chụp lại ảnh rõ hơn.");
        }
        if ("NON_SHRIMP".equals(aiStatus)) {
            log.info("[AiDoctor] traceId={} NON_SHRIMP image", traceId);
            throw new BadRequestException(
                    "Không phát hiện tôm trong ảnh. Vui lòng điều chỉnh góc chụp và thử lại.");
        }
        if ("HEALTHY".equals(aiStatus)) {
            log.info("[AiDoctor] traceId={} HEALTHY shrimp", traceId);
            return AiDoctorDiagnosisResponse.builder()
                    .diagnosisId("healthy_" + traceId)
                    .status("HEALTHY")
                    .imageUrl(diagnosisImageUrl)
                    .build();
        }

        AiPredictionItem finalPrediction = predictResponse.getFinalPrediction();

        if (finalPrediction == null || finalPrediction.getDiseaseCode() == null) {
            log.warn("[AiDoctor] traceId={} AI không nhận ra bệnh", traceId);
            throw new BadRequestException(
                    "Không thể nhận dạng bệnh từ ảnh. Vui lòng chụp ảnh rõ hơn và thử lại.");
        }

        String diseaseCode = finalPrediction.getDiseaseCode();
        log.info("[AiDoctor] traceId={} predict OK: diseaseCode={}, confidence={}% ",
                traceId, diseaseCode, finalPrediction.getConfidencePercent());

        AiDoctorDiagnosisResponse response = aiKnowledgeService.enrichDiagnosis(
                predictResponse,
                finalPrediction,
                diagnosisImageUrl,
                normalizedSymptoms,
                sessionId != null ? sessionId : "diag_" + traceId,
                userId);

        if (userId != null && "DISEASE".equalsIgnoreCase(response.getStatus())) {
            try {
                Long historyId = diagnosisHistoryService.saveDiagnosisHistory(response, userId, normalizedSymptoms);
                response.setDiagnosisId(String.valueOf(historyId));
            } catch (Exception e) {
                log.error("[AiDoctor] traceId={} History save fail (graceful): {}", traceId, e.getMessage());
            }
        }

        log.info("[AiDoctor] traceId={} done: userId={}, diagnosisId={}", traceId, userId, response.getDiagnosisId());
        return response;
    }

    // =========================================================
    // PUBLIC: Gọi Gemini tạo phác đồ (bước 2 — user chủ động trigger)
    // =========================================================

    public AiDoctorDiagnosisResponse generatePrescription(Long diagnosisId, Long userId) {
        AiDoctorDiagnosisHistory history = historyRepository.findByIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new NotFoundException("AI_DOCTOR_DIAGNOSIS_NOT_FOUND"));

        String diseaseCode = history.getFinalDiseaseCode();
        if (diseaseCode == null) {
            throw new BadRequestException("Không thể tạo phác đồ cho ca chẩn đoán này");
        }
        AiDoctorDiagnosisResponse response = aiKnowledgeService.buildPrescriptionFromApprovedKnowledge(diseaseCode, diagnosisId);
        diagnosisHistoryService.updateWithPrescription(
                diagnosisId,
                response.getCauses() != null ? response.getCauses() : Collections.emptyList(),
                response.getSignsSummary(),
                response.getTreatmentStages() != null ? response.getTreatmentStages() : Collections.emptyList());

        log.info("[AiDoctor-Prescription] diagnosisId={}, diseaseCode={}, stages={}",
                diagnosisId, diseaseCode, response.getTreatmentStages() != null ? response.getTreatmentStages().size() : 0);
        return response;
    }

    private String uploadAnnotatedImageGracefully(AiPredictResponse predictResponse, String traceId) {
        String annotatedImage = predictResponse.getAnnotatedImage();
        if (annotatedImage == null || annotatedImage.isBlank()) {
            return null;
        }

        try {
            return cloudinaryService.uploadImage(annotatedImage, "ai-doctor/diagnosis");
        } catch (Exception e) {
            log.warn("[AiDoctor] traceId={} upload annotated image fail (graceful): {}",
                    traceId, e.getMessage());
            return null;
        }
    }
}

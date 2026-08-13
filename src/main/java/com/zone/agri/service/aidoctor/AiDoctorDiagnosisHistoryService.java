package com.zone.agri.service.aidoctor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.dto.response.ai.AiDoctorHistoryItemResponse;
import com.zone.agri.dto.response.ai.AiDoctorHistoryListResponse;
import com.zone.agri.dto.response.ai.DiseaseResponse;
import com.zone.agri.dto.response.ai.TopPredictionResponse;
import com.zone.agri.dto.response.ai.TreatmentStageResponse;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase BE-4: Quản lý lịch sử chẩn đoán AI Doctor.
 *
 * Trách nhiệm:
 * - Lưu history sau mỗi diagnosis (full hoặc partial nếu prescription fail)
 * - Trả danh sách history của đúng user (phân trang)
 * - Trả chi tiết 1 ca — FE render lại result/treatment mà không cần gọi AI lại
 * - Bảo vệ ownership: user chỉ xem được history của chính mình
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiDoctorDiagnosisHistoryService {

    private final AiDoctorDiagnosisHistoryRepository historyRepository;
    private final AiDiseaseKnowledgeRepository diseaseKnowledgeRepository;
    private final ObjectMapper objectMapper;

    // =========================================================
    // SAVE — gọi từ AiDoctorDiagnosisService sau khi build response
    // =========================================================

    /**
     * Lưu một bản ghi chẩn đoán vào history.
     *
     * Lưu ý: phương thức này KHÔNG được gọi bên trong @Transactional của luồng diagnosis
     * vì nếu history save fail, không muốn rollback toàn bộ diagnosis.
     * Caller (AiDoctorDiagnosisService) tự catch Exception và log gracefully.
     *
     * @return id của bản ghi vừa lưu — dùng làm diagnosisId thực sự trả về FE
     */
    @Transactional
    public Long saveDiagnosisHistory(AiDoctorDiagnosisResponse response, Long userId, String userSymptoms) {
        DiseaseResponse disease = response.getDisease();

        AiDoctorDiagnosisHistory history = AiDoctorDiagnosisHistory.builder()
                .userId(userId)
                .imageUrl(response.getImageUrl())
                .userSymptoms(userSymptoms)
                .finalDiseaseCode(disease != null ? disease.getCode() : null)
                .finalDiseaseNameVi(disease != null ? disease.getNameVi() : null)
                .finalDiseaseNameEn(disease != null ? disease.getNameEn() : null)
                .finalConfidencePercent(disease != null ? disease.getConfidencePercent() : null)
                .topPredictionsJson(toJson(response.getTopPredictions()))
                .causesJson(toJson(response.getCauses()))
                .signsSummary(response.getSignsSummary())
                .treatmentStagesJson(toJson(response.getTreatmentStages()))
                .purchaseUrl(response.getPurchaseUrl())
                .needsClarification(response.getNeedsClarification())
                .status(response.getStatus())
                .aiDescription(response.getAiDescription())
                .build();

        AiDoctorDiagnosisHistory saved = historyRepository.save(history);
        log.info("[AiDoctor-History] saved: userId={}, historyId={}, diseaseCode={}",
                userId, saved.getId(), saved.getFinalDiseaseCode());
        return saved.getId();
    }

    // =========================================================
    // UPDATE — cập nhật prescription sau khi user chủ động lấy phác đồ từ kho tri thức đã duyệt
    // =========================================================

    @Transactional
    public void updateWithPrescription(Long historyId, List<String> causes,
                                       String signsSummary, List<TreatmentStageResponse> treatmentStages) {
        AiDoctorDiagnosisHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new com.zone.agri.exception.NotFoundException("AI_DOCTOR_DIAGNOSIS_NOT_FOUND"));
        history.setCausesJson(toJson(causes));
        history.setSignsSummary(signsSummary);
        history.setTreatmentStagesJson(toJson(treatmentStages));
        historyRepository.save(history);
        log.info("[AiDoctor-History] updated prescription: historyId={}", historyId);
    }

    /**
     * Cập nhật lại danh tính bệnh sau khi luồng hỏi-đáp AI Doctor (AiDoctorClarifyService) chốt được
     * bệnh chính xác — ban đầu history được lưu với disease "đoán" từ YOLO lúc confidence còn thấp,
     * cần sửa lại để GET /history và GET /diagnosis/{id} không còn hiển thị bệnh đoán sai.
     */
    @Transactional
    public void updateWithClarifiedDisease(Long historyId, DiseaseResponse disease, List<String> causes,
                                           String signsSummary, List<TreatmentStageResponse> treatmentStages) {
        AiDoctorDiagnosisHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new NotFoundException("AI_DOCTOR_DIAGNOSIS_NOT_FOUND"));
        if (disease != null) {
            history.setFinalDiseaseCode(disease.getCode());
            history.setFinalDiseaseNameVi(disease.getNameVi());
            history.setFinalDiseaseNameEn(disease.getNameEn());
        }
        history.setCausesJson(toJson(causes));
        history.setSignsSummary(signsSummary);
        history.setTreatmentStagesJson(toJson(treatmentStages));
        // Clarify đã chốt được bệnh — không còn là bản ghi "đang chờ xác nhận" nữa.
        history.setNeedsClarification(false);
        historyRepository.save(history);
        log.info("[AiDoctor-History] updated after clarify: historyId={}, diseaseCode={}",
                historyId, disease != null ? disease.getCode() : null);
    }

    // =========================================================
    // LIST — GET /api/ai-doctor/history
    // =========================================================

    /**
     * Trả danh sách lịch sử chẩn đoán của user, mới nhất trước.
     * Chỉ trả thông tin tóm tắt — FE dùng để render danh sách, không render full detail.
     */
    @Transactional(readOnly = true)
    public AiDoctorHistoryListResponse getMyDiagnosisHistory(Long userId, Pageable pageable) {
        Page<AiDoctorDiagnosisHistory> page = historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<AiDoctorHistoryItemResponse> items = page.getContent().stream()
                .map(this::toHistoryItemResponse)
                .collect(Collectors.toList());

        log.debug("[AiDoctor-History] list: userId={}, total={}, page={}, size={}",
                userId, page.getTotalElements(), page.getNumber(), page.getSize());

        return AiDoctorHistoryListResponse.builder()
                .items(items)
                .total(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }

    // =========================================================
    // DETAIL — GET /api/ai-doctor/diagnosis/{id}
    // =========================================================

    /**
     * Trả full detail của một ca chẩn đoán.
     *
     * Security: dùng findByIdAndUserId — nếu id không tồn tại HOẶC không thuộc user
     * → đều throw NotFoundException (không để lộ sự tồn tại của record người khác).
     *
     * Response shape gần giống POST /api/ai-doctor/diagnosis, thêm createdAt
     * để FE biết thời điểm chẩn đoán.
     */
    @Transactional(readOnly = true)
    public AiDoctorDiagnosisResponse getDiagnosisDetail(Long diagnosisId, Long userId) {
        AiDoctorDiagnosisHistory history = historyRepository.findByIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new NotFoundException("AI_DOCTOR_DIAGNOSIS_NOT_FOUND"));
        return toFullDetailResponse(history);
    }

    // =========================================================
    // PRIVATE — mapping
    // =========================================================

    private AiDoctorHistoryItemResponse toHistoryItemResponse(AiDoctorDiagnosisHistory h) {
        DiseaseResponse disease = DiseaseResponse.builder()
                .code(h.getFinalDiseaseCode())
                .nameVi(h.getFinalDiseaseNameVi())
                .nameEn(h.getFinalDiseaseNameEn())
                .confidencePercent(h.getFinalConfidencePercent())
                .imageUrls(resolveDiseaseImageUrls(h.getFinalDiseaseCode()))
                .build();

        return AiDoctorHistoryItemResponse.builder()
                .diagnosisId(String.valueOf(h.getId()))
                .createdAt(h.getCreatedAt())
                .imageUrl(h.getImageUrl())
                .disease(disease)
                .needsClarification(h.getNeedsClarification())
                .build();
    }

    private AiDoctorDiagnosisResponse toFullDetailResponse(AiDoctorDiagnosisHistory h) {
        DiseaseResponse disease = DiseaseResponse.builder()
                .code(h.getFinalDiseaseCode())
                .nameVi(h.getFinalDiseaseNameVi())
                .nameEn(h.getFinalDiseaseNameEn())
                .confidencePercent(h.getFinalConfidencePercent())
                .imageUrls(resolveDiseaseImageUrls(h.getFinalDiseaseCode()))
                .build();

        List<TopPredictionResponse> topPredictions = fromJsonList(
                h.getTopPredictionsJson(),
                new TypeReference<List<TopPredictionResponse>>() {});

        List<String> causes = fromJsonList(
                h.getCausesJson(),
                new TypeReference<List<String>>() {});

        List<TreatmentStageResponse> treatmentStages = fromJsonList(
                h.getTreatmentStagesJson(),
                new TypeReference<List<TreatmentStageResponse>>() {});

        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId(String.valueOf(h.getId()))
                .imageUrl(h.getImageUrl())
                .disease(disease)
                .topPredictions(topPredictions.isEmpty() ? null : topPredictions)
                .causes(causes.isEmpty() ? null : causes)
                .signsSummary(h.getSignsSummary())
                .treatmentStages(treatmentStages.isEmpty() ? null : treatmentStages)
                .purchaseUrl(h.getPurchaseUrl())
                .needsClarification(h.getNeedsClarification())
                .createdAt(h.getCreatedAt())
                .build();
    }

    // =========================================================
    // PRIVATE — JSON helpers (serialize/deserialize tập trung)
    // =========================================================

    /**
     * Serialize object → JSON string để lưu vào DB.
     * Trả null nếu object null hoặc serialize lỗi.
     */
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[AiDoctor-History] JSON serialize fail (field skipped): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Deserialize JSON string → List<T>.
     * Fallback về emptyList nếu json null/blank hoặc parse lỗi.
     * Không crash API khi đọc dữ liệu cũ/corrupted.
     */
    private <T> List<T> fromJsonList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("[AiDoctor-History] JSON parse fail (fallback emptyList): type={}, error={}",
                    typeRef.getType().getTypeName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> resolveDiseaseImageUrls(String diseaseCode) {
        if (diseaseCode == null || diseaseCode.isBlank()) {
            return null;
        }
        return diseaseKnowledgeRepository.findByCode(diseaseCode)
                .filter(disease -> disease.getStatus() == AiKnowledgeStatus.APPROVED)
                .filter(disease -> !Boolean.FALSE.equals(disease.getEnabled()))
                .map(AiDiseaseKnowledge::getImageUrlsJson)
                .map(json -> fromJsonList(json, new TypeReference<List<String>>() {}))
                .filter(imageUrls -> !imageUrls.isEmpty())
                .orElse(null);
    }
}

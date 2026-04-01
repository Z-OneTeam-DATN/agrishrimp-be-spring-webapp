package com.zone.agri.service.miniapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.miniapp.response.*;
import com.zone.agri.entity.MiniAppDiagnosisHistory;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.MiniAppDiagnosisHistoryRepository;
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
 * Phase BE-4: Quản lý lịch sử chẩn đoán Mini App.
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
public class MiniAppDiagnosisHistoryService {

    private final MiniAppDiagnosisHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    // =========================================================
    // SAVE — gọi từ MiniAppDiagnosisService sau khi build response
    // =========================================================

    /**
     * Lưu một bản ghi chẩn đoán vào history.
     *
     * Lưu ý: phương thức này KHÔNG được gọi bên trong @Transactional của luồng diagnosis
     * vì nếu history save fail, không muốn rollback toàn bộ diagnosis.
     * Caller (MiniAppDiagnosisService) tự catch Exception và log gracefully.
     *
     * @return id của bản ghi vừa lưu — dùng làm diagnosisId thực sự trả về FE
     */
    @Transactional
    public Long saveDiagnosisHistory(MiniAppDiagnosisResponse response, Long userId, String userSymptoms) {
        DiseaseResponse disease = response.getDisease();

        MiniAppDiagnosisHistory history = MiniAppDiagnosisHistory.builder()
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
                .build();

        MiniAppDiagnosisHistory saved = historyRepository.save(history);
        log.info("[MiniApp-History] saved: userId={}, historyId={}, diseaseCode={}",
                userId, saved.getId(), saved.getFinalDiseaseCode());
        return saved.getId();
    }

    // =========================================================
    // LIST — GET /api/miniapp/history
    // =========================================================

    /**
     * Trả danh sách lịch sử chẩn đoán của user, mới nhất trước.
     * Chỉ trả thông tin tóm tắt — FE dùng để render danh sách, không render full detail.
     */
    @Transactional(readOnly = true)
    public MiniAppHistoryListResponse getMyDiagnosisHistory(Long userId, Pageable pageable) {
        Page<MiniAppDiagnosisHistory> page = historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<MiniAppHistoryItemResponse> items = page.getContent().stream()
                .map(this::toHistoryItemResponse)
                .collect(Collectors.toList());

        log.debug("[MiniApp-History] list: userId={}, total={}, page={}, size={}",
                userId, page.getTotalElements(), page.getNumber(), page.getSize());

        return MiniAppHistoryListResponse.builder()
                .items(items)
                .total(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }

    // =========================================================
    // DETAIL — GET /api/miniapp/diagnosis/{id}
    // =========================================================

    /**
     * Trả full detail của một ca chẩn đoán.
     *
     * Security: dùng findByIdAndUserId — nếu id không tồn tại HOẶC không thuộc user
     * → đều throw NotFoundException (không để lộ sự tồn tại của record người khác).
     *
     * Response shape gần giống POST /api/miniapp/diagnosis, thêm createdAt
     * để FE biết thời điểm chẩn đoán.
     */
    @Transactional(readOnly = true)
    public MiniAppDiagnosisResponse getDiagnosisDetail(Long diagnosisId, Long userId) {
        MiniAppDiagnosisHistory history = historyRepository.findByIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new NotFoundException("MINIAPP_DIAGNOSIS_NOT_FOUND"));
        return toFullDetailResponse(history);
    }

    // =========================================================
    // PRIVATE — mapping
    // =========================================================

    private MiniAppHistoryItemResponse toHistoryItemResponse(MiniAppDiagnosisHistory h) {
        DiseaseResponse disease = DiseaseResponse.builder()
                .code(h.getFinalDiseaseCode())
                .nameVi(h.getFinalDiseaseNameVi())
                .nameEn(h.getFinalDiseaseNameEn())
                .confidencePercent(h.getFinalConfidencePercent())
                .build();

        return MiniAppHistoryItemResponse.builder()
                .diagnosisId(String.valueOf(h.getId()))
                .createdAt(h.getCreatedAt())
                .imageUrl(h.getImageUrl())
                .disease(disease)
                .build();
    }

    private MiniAppDiagnosisResponse toFullDetailResponse(MiniAppDiagnosisHistory h) {
        DiseaseResponse disease = DiseaseResponse.builder()
                .code(h.getFinalDiseaseCode())
                .nameVi(h.getFinalDiseaseNameVi())
                .nameEn(h.getFinalDiseaseNameEn())
                .confidencePercent(h.getFinalConfidencePercent())
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

        return MiniAppDiagnosisResponse.builder()
                .diagnosisId(String.valueOf(h.getId()))
                .imageUrl(h.getImageUrl())
                .disease(disease)
                .topPredictions(topPredictions.isEmpty() ? null : topPredictions)
                .causes(causes.isEmpty() ? null : causes)
                .signsSummary(h.getSignsSummary())
                .treatmentStages(treatmentStages.isEmpty() ? null : treatmentStages)
                .purchaseUrl(h.getPurchaseUrl())
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
            log.warn("[MiniApp-History] JSON serialize fail (field skipped): {}", e.getMessage());
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
            log.warn("[MiniApp-History] JSON parse fail (fallback emptyList): type={}, error={}",
                    typeRef.getType().getTypeName(), e.getMessage());
            return Collections.emptyList();
        }
    }
}

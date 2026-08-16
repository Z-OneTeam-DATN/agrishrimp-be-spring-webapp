package com.zone.agri.service.aidoctor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.response.ai.AiDoctorConversationTurnResponse;
import com.zone.agri.dto.response.ai.AiDoctorDailyRecordListResponse;
import com.zone.agri.dto.response.ai.DiseaseResponse;
import com.zone.agri.dto.response.ai.TreatmentStageResponse;
import com.zone.agri.dto.response.ai.TreatmentStageSelectionResponse;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiKnowledgeChatLog;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiKnowledgeChatLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Sổ khám" — gộp hội thoại chat chữ + chẩn đoán qua ảnh trong 1 ngày của 1 user thành 1 mục.
 * Sinh hoàn toàn on-demand từ 2 nguồn đã có sẵn (AiKnowledgeChatLog, AiDoctorDiagnosisHistory),
 * không lưu entity/bảng riêng nào — tránh rủi ro đồng bộ giữa log thô và bản tóm tắt lưu sẵn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiDoctorDailyRecordService {

    /** Kênh nguồn của chat AI Doctor riêng tư (AiDoctorController.chat) — loại bỏ log public/test/CSKH. */
    private static final String PRIVATE_SOURCE_CHANNEL = "AI_DOCTOR_PRIVATE";

    private static final int LOOKBACK_DAYS = 90;

    private final AiKnowledgeChatLogRepository chatLogRepository;
    private final AiDoctorDiagnosisHistoryRepository diagnosisHistoryRepository;
    private final AiDiseaseKnowledgeRepository diseaseKnowledgeRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AiDoctorDailyRecordListResponse getDailyRecordDates(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);

        // Luong gui anh (AiDoctorDiagnosisService/AiDoctorClarifyService) khong ghi vao
        // AiKnowledgeChatLog — mot ngay chi gui anh, khong go chat chu nao, van phai xuat hien
        // trong so kham nen BAT BUOC phai UNION ca 2 nguon, khong chi lay 1 nguon.
        TreeSet<LocalDate> days = new TreeSet<>(Collections.reverseOrder());
        chatLogRepository.findCreatedAtByUserIdAndSourceChannelSince(userId, PRIVATE_SOURCE_CHANNEL, since)
                .forEach(createdAt -> days.add(createdAt.toLocalDate()));
        diagnosisHistoryRepository.findCreatedAtByUserIdSince(userId, since)
                .forEach(createdAt -> days.add(createdAt.toLocalDate()));

        return AiDoctorDailyRecordListResponse.builder()
                .dates(days.stream().map(LocalDate::toString).toList())
                .build();
    }

    /**
     * Phat lai (replay) dung thu tu cac bong bong chat cua 1 NGAY (hom nay hoac 1 ngay da qua) — de
     * FE khoi phuc lai giao dien thay vi mat sach du lieu, hoac xem lai dung 1 ngay trong sidebar.
     * Khong bao gom cac luot hoi-dap lam ro benh (AiDoctorClarifySession luu o bang khac, gioi han
     * co chu dich cua tinh nang nay).
     */
    @Transactional(readOnly = true)
    public List<AiDoctorConversationTurnResponse> getConversation(Long userId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<AiDoctorConversationTurnResponse> turns = new ArrayList<>();
        chatLogRepository.findByUserIdAndSourceChannelAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        userId, PRIVATE_SOURCE_CHANNEL, startOfDay, endOfDay)
                .forEach(log -> turns.add(toChatTurn(log)));
        diagnosisHistoryRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(userId, startOfDay, endOfDay)
                .forEach(history -> turns.add(toDiagnosisTurn(history)));

        turns.sort(Comparator.comparing(AiDoctorConversationTurnResponse::getCreatedAt));
        return turns;
    }

    private AiDoctorConversationTurnResponse toChatTurn(AiKnowledgeChatLog log) {
        return AiDoctorConversationTurnResponse.builder()
                .type("CHAT")
                .createdAt(log.getCreatedAt())
                .questionText(log.getQuestionText())
                .answerHtml(log.getAnswerText())
                .build();
    }

    private AiDoctorConversationTurnResponse toDiagnosisTurn(AiDoctorDiagnosisHistory history) {
        // HEALTHY/UNRECOGNIZED khong co finalDiseaseCode — khong build disease "rong" (toan null),
        // de FE biet ro rang khong co benh cu the nao thay vi hien 1 object rong.
        DiseaseResponse disease = history.getFinalDiseaseCode() != null
                ? DiseaseResponse.builder()
                        .code(history.getFinalDiseaseCode())
                        .nameVi(history.getFinalDiseaseNameVi())
                        .nameEn(history.getFinalDiseaseNameEn())
                        .confidencePercent(history.getFinalConfidencePercent())
                        .imageUrls(resolveDiseaseImageUrls(history.getFinalDiseaseCode()))
                        .build()
                : null;

        return AiDoctorConversationTurnResponse.builder()
                .type("DIAGNOSIS")
                .createdAt(history.getCreatedAt())
                .diagnosisId(String.valueOf(history.getId()))
                .userSymptoms(history.getUserSymptoms())
                .imageUrl(history.getImageUrl())
                .disease(disease)
                .signsSummary(history.getSignsSummary())
                .needsClarification(history.getNeedsClarification())
                .stageSelection(resolveStageSelection(history))
                // Ban ghi cu truoc patch nay chi tung luu DISEASE nen status co the null — coi null
                // nhu "DISEASE" de khong vo du lieu lich su cu.
                .status(history.getStatus() != null ? history.getStatus() : "DISEASE")
                .aiDescription(history.getAiDescription())
                .build();
    }

    private TreatmentStageSelectionResponse resolveStageSelection(AiDoctorDiagnosisHistory history) {
        if (history.getFinalDiseaseCode() == null || Boolean.TRUE.equals(history.getNeedsClarification())) {
            return null;
        }
        return diseaseKnowledgeRepository.findByCode(history.getFinalDiseaseCode())
                .filter(disease -> disease.getStatus() == AiKnowledgeStatus.APPROVED)
                .filter(disease -> !Boolean.FALSE.equals(disease.getEnabled()))
                .map(AiDiseaseKnowledge::getTreatmentStagesJson)
                .map(this::readStageSelection)
                .orElse(null);
    }

    private TreatmentStageSelectionResponse readStageSelection(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> rawStages = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<TreatmentStageResponse> stages = rawStages.stream()
                    .map(row -> TreatmentStageResponse.builder()
                            .stageTitle(row.get("stageTitle") instanceof String title ? title : null)
                            .build())
                    .toList();
            return stages.size() > 1 ? TreatmentStageSelectionResponse.fromStages(stages) : null;
        } catch (Exception ex) {
            log.warn("[AiDoctor-DailyRecord] treatment stage json parse fail: {}", ex.getMessage());
            return null;
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
                .map(this::readImageUrls)
                .filter(imageUrls -> !imageUrls.isEmpty())
                .orElse(null);
    }

    private List<String> readImageUrls(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.warn("[AiDoctor-DailyRecord] disease image json parse fail: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}

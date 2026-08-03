package com.zone.agri.service.aidoctor;

import com.zone.agri.dto.response.ai.AiClarifyCandidateSummary;
import com.zone.agri.dto.response.ai.AiDoctorDailyRecordDetailResponse;
import com.zone.agri.dto.response.ai.AiDoctorDailyRecordListResponse;
import com.zone.agri.dto.response.ai.AiDoctorHistoryItemResponse;
import com.zone.agri.dto.response.ai.DiseaseResponse;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.AiKnowledgeChatLog;
import com.zone.agri.entity.enums.AiKnowledgeMatchType;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
import com.zone.agri.repository.AiKnowledgeChatLogRepository;
import com.zone.agri.service.ai.AiKnowledgeService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
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
    private final AiKnowledgeService aiKnowledgeService;

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

    @Transactional(readOnly = true)
    public AiDoctorDailyRecordDetailResponse getDailyRecordDetail(Long userId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<AiKnowledgeChatLog> chatLogs = chatLogRepository
                .findByUserIdAndSourceChannelAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        userId, PRIVATE_SOURCE_CHANNEL, startOfDay, endOfDay);

        List<String> symptomsDescribed = chatLogs.stream()
                .map(AiKnowledgeChatLog::getQuestionText)
                .filter(text -> text != null && !text.isBlank())
                .toList();

        List<DiseaseResponse> diseasesDiscussed = chatLogs.stream()
                .filter(log -> Boolean.TRUE.equals(log.getMatched()) && log.getMatchedType() == AiKnowledgeMatchType.DISEASE_KNOWLEDGE)
                .map(AiKnowledgeChatLog::getMatchedKnowledgeCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .map(aiKnowledgeService::findApprovedCandidate)
                .flatMap(Optional::stream)
                .map(this::toDiseaseResponse)
                .toList();

        List<AiDoctorHistoryItemResponse> diagnoses = diagnosisHistoryRepository
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(userId, startOfDay, endOfDay)
                .stream()
                .map(this::toHistoryItemResponse)
                .toList();

        return AiDoctorDailyRecordDetailResponse.builder()
                .date(date.toString())
                .symptomsDescribed(symptomsDescribed)
                .diseasesDiscussed(diseasesDiscussed)
                .diagnoses(diagnoses)
                .build();
    }

    private DiseaseResponse toDiseaseResponse(AiClarifyCandidateSummary candidate) {
        return DiseaseResponse.builder()
                .code(candidate.getDiseaseCode())
                .nameVi(candidate.getNameVi())
                .nameEn(candidate.getNameEn())
                .build();
    }

    // Copy tu AiDoctorDiagnosisHistoryService.toHistoryItemResponse — chu dinh khong tai su dung
    // truc tiep de tranh coupling 2 service khong lien quan truc tiep ve muc dich.
    private AiDoctorHistoryItemResponse toHistoryItemResponse(AiDoctorDiagnosisHistory history) {
        DiseaseResponse disease = DiseaseResponse.builder()
                .code(history.getFinalDiseaseCode())
                .nameVi(history.getFinalDiseaseNameVi())
                .nameEn(history.getFinalDiseaseNameEn())
                .confidencePercent(history.getFinalConfidencePercent())
                .build();

        return AiDoctorHistoryItemResponse.builder()
                .diagnosisId(String.valueOf(history.getId()))
                .createdAt(history.getCreatedAt())
                .imageUrl(history.getImageUrl())
                .disease(disease)
                .needsClarification(history.getNeedsClarification())
                .build();
    }
}

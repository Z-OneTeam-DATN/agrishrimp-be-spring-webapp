package com.zone.agri.service.aidoctor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.ai.AiClarifyLlmResult;
import com.zone.agri.dto.ai.AiClarifyTurn;
import com.zone.agri.dto.request.ai.AiDoctorClarifyRequest;
import com.zone.agri.dto.response.ai.AiClarifyCandidateSummary;
import com.zone.agri.dto.response.ai.AiDoctorClarifyResponse;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.dto.response.ai.DiseaseResponse;
import com.zone.agri.entity.AiDoctorClarifySession;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.AiKnowledgeReviewCase;
import com.zone.agri.entity.enums.AiClarifySessionStatus;
import com.zone.agri.entity.enums.AiReviewCaseReason;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.AiDoctorClarifySessionRepository;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
import com.zone.agri.service.ai.AiKnowledgeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Luồng LLM hỏi làm rõ bệnh khi ảnh AI Doctor có độ tin cậy thấp (needsClarification=true).
 *
 * Nguyên tắc bắt buộc (đã thống nhất với product owner):
 *  1. Gemini chỉ được chọn disease_code nằm trong tập candidate đã khoá + đang APPROVED — không tự bịa bệnh.
 *  2. Câu hỏi chỉ dựa trên symptomKeywordsRaw/signsSummary của các candidate đó — không hỏi ngoài phạm vi.
 *  3. Phác đồ luôn lấy từ buildPrescriptionFromApprovedKnowledge() (DB đã duyệt) — Gemini không tự soạn.
 *  4. Không giới hạn số vòng hỏi ở tầng UX, nhưng có trần an toàn kỹ thuật ẩn (ai.doctor.clarify.max-turns)
 *     — chạm trần thì escalate sang review case, không lặp vô hạn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiDoctorClarifyService {

    private static final String ESCALATION_MESSAGE =
            "Ca này hơi khó, bác sĩ cần kỹ sư nông nghiệp xem lại kỹ hơn. "
                    + "Đội ngũ AgriShrimp sẽ liên hệ hỗ trợ bà con sớm nhất có thể.";

    private final GeminiClarifyClient geminiClarifyClient;
    private final AiDoctorClarifySessionRepository sessionRepository;
    private final AiDoctorDiagnosisHistoryRepository historyRepository;
    private final AiDoctorDiagnosisHistoryService historyService;
    private final AiKnowledgeService aiKnowledgeService;
    private final ObjectMapper objectMapper;

    @Value("${ai.doctor.clarify.max-turns:8}")
    private int maxTurns;

    /**
     * KHÔNG @Transactional: phương thức này gọi Gemini (HTTP ra ngoài, tối đa ~40s) — nếu bọc
     * trong 1 transaction sẽ giữ connection DB mở suốt thời gian đó, dễ cạn connection pool khi
     * nhiều người dùng hỏi cùng lúc. Mỗi thao tác DB (sessionRepository.save/find,
     * aiKnowledgeService.*, historyService.*) đã tự có transaction riêng ở tầng repository/service
     * tương ứng, không cần một transaction bao trùm cả cuộc gọi Gemini.
     */
    public AiDoctorClarifyResponse continueClarify(String diagnosisId, AiDoctorClarifyRequest request, Long userId, String sourceChannel) {
        String normalizedDiagnosisId = trimToNull(diagnosisId);
        if (normalizedDiagnosisId == null) {
            throw new BadRequestException("Thiếu mã chẩn đoán để hỏi thêm.");
        }

        Optional<AiDoctorClarifySession> existing = sessionRepository.findByDiagnosisId(normalizedDiagnosisId);

        AiDoctorClarifySession session;
        if (existing.isPresent()) {
            session = existing.get();
            if (!ownerMatches(session, userId)) {
                // Trường hợp thực tế nhất: token đăng nhập hết hạn giữa hội thoại khiến request
                // bị chuyển nhầm từ luồng private sang public (hoặc ngược lại). Trước đây điều
                // này khiến bootstrapSession() cố insert phiên mới trùng diagnosis_id và crash do
                // vi phạm unique constraint — giờ trả lỗi rõ ràng ngay từ đầu.
                throw new BadRequestException(
                        "Phiên trò chuyện này không thuộc phiên đăng nhập hiện tại. Vui lòng đăng nhập lại để tiếp tục hỏi bác sĩ.");
            }

            if (session.getStatus() != AiClarifySessionStatus.ACTIVE) {
                // Idempotent replay — FE gọi lại (double-submit, mất mạng...) trên phiên đã kết thúc.
                return toResponse(session);
            }

            String answer = trimToNull(request != null ? request.getAnswer() : null);
            if (answer == null) {
                List<AiClarifyTurn> turnsSoFar = readTurns(session);
                if (!turnsSoFar.isEmpty()) {
                    // Phiên đã hỏi ít nhất 1 câu nhưng request này không kèm câu trả lời → đây là
                    // request bị gửi lại (mạng chập chờn, double-submit của lượt bootstrap ban đầu),
                    // không phải một lượt hỏi mới. Trả lại đúng câu hỏi gần nhất, KHÔNG hỏi Gemini
                    // lần nữa để tránh ăn vào trần an toàn cho một tương tác không có thật.
                    return replayLastQuestion(session, turnsSoFar);
                }
                // turnsSoFar rỗng: đây thực sự là lượt bootstrap đầu tiên trên 1 phiên đã tồn tại
                // (vd request trước bị mất phản hồi ngay sau khi lưu) — cho qua bình thường.
            } else {
                appendFarmerTurn(session, answer);
                session = sessionRepository.save(session);
            }
        } else {
            session = bootstrapSession(normalizedDiagnosisId, request, userId, sourceChannel);
            if (session.getStatus() != AiClarifySessionStatus.ACTIVE) {
                return toResponse(session);
            }
        }

        if (session.getTurnCount() >= maxTurns) {
            log.info("[AiDoctorClarify] diagnosisId={} cham tran an toan ({} luot), escalate", normalizedDiagnosisId, maxTurns);
            escalate(session);
            return toResponse(session);
        }

        return callGeminiAndAdvance(session);
    }

    private boolean ownerMatches(AiDoctorClarifySession session, Long callerUserId) {
        return java.util.Objects.equals(session.getUserId(), callerUserId);
    }

    // =========================================================
    // PRIVATE — orchestration
    // =========================================================

    private AiDoctorClarifySession bootstrapSession(String diagnosisId, AiDoctorClarifyRequest request, Long userId, String sourceChannel) {
        List<String> requestedCodes = request != null ? request.getCandidateDiseaseCodes() : null;
        List<AiClarifyCandidateSummary> candidates = aiKnowledgeService.resolveApprovedCandidates(requestedCodes);

        AiDoctorDiagnosisHistory history = resolveHistory(diagnosisId, userId);

        // Khách vãng lai không có history DB (userId==null → resolveHistory luôn trả null) — dùng
        // imageUrl/initialSymptoms FE gửi kèm từ response /diagnosis ban đầu để không mất ảnh và
        // triệu chứng đã nhập trong suốt cuộc hỏi-đáp. User đăng nhập vẫn ưu tiên dữ liệu từ history
        // (nguồn đáng tin cậy hơn giá trị client tự gửi lại).
        String imageUrl = history != null
                ? history.getImageUrl()
                : trimToNull(request != null ? request.getImageUrl() : null);
        String initialSymptoms = history != null
                ? history.getUserSymptoms()
                : trimToNull(request != null ? request.getInitialSymptoms() : null);

        AiDoctorClarifySession session = AiDoctorClarifySession.builder()
                .diagnosisId(diagnosisId)
                .userId(userId)
                .diagnosisHistoryId(history != null ? history.getId() : null)
                .sourceChannel(sourceChannel)
                .imageUrl(imageUrl)
                .initialSymptoms(initialSymptoms)
                .aiSuggestedDiseaseCode(history != null ? history.getFinalDiseaseCode() : null)
                .candidateDiseaseCodesJson(writeJson(candidates.stream().map(AiClarifyCandidateSummary::getDiseaseCode).toList()))
                .conversationJson(writeJson(Collections.emptyList()))
                .turnCount(0)
                .status(AiClarifySessionStatus.ACTIVE)
                .build();
        session = sessionRepository.save(session);

        if (candidates.isEmpty()) {
            log.warn("[AiDoctorClarify] diagnosisId={} khong co candidate APPROVED hop le, escalate ngay", diagnosisId);
            escalate(session);
        }
        return session;
    }

    private AiDoctorDiagnosisHistory resolveHistory(String diagnosisId, Long userId) {
        if (userId == null) {
            return null; // khách vãng lai: diagnosisId chỉ là chuỗi tạm, không có history row
        }
        Long numericId = parseNumericId(diagnosisId);
        if (numericId == null) {
            return null;
        }
        return historyRepository.findByIdAndUserId(numericId, userId).orElse(null);
    }

    private Long parseNumericId(String diagnosisId) {
        try {
            return Long.valueOf(diagnosisId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AiDoctorClarifyResponse callGeminiAndAdvance(AiDoctorClarifySession session) {
        List<AiClarifyCandidateSummary> candidates = readCandidates(session);
        List<AiClarifyTurn> turnsSoFar = readTurns(session);

        AiClarifyLlmResult llmResult;
        try {
            llmResult = geminiClarifyClient.clarify(candidates, turnsSoFar);
        } catch (Exception ex) {
            log.warn("[AiDoctorClarify] diagnosisId={} Gemini call fail, escalate: {}", session.getDiagnosisId(), ex.getMessage());
            escalate(session);
            return toResponse(session);
        }

        String responseType = llmResult.getResponseType();

        if ("DECISION".equalsIgnoreCase(responseType)) {
            Optional<AiClarifyCandidateSummary> validated = validateDecision(session, llmResult.getDiseaseCode());
            if (validated.isEmpty()) {
                log.warn("[AiDoctorClarify] diagnosisId={} Gemini decision khong hop le (diseaseCode={}), escalate",
                        session.getDiagnosisId(), llmResult.getDiseaseCode());
                escalate(session);
                return toResponse(session);
            }
            return finalizeDecision(session, validated.get());
        }

        if ("QUESTION".equalsIgnoreCase(responseType) && trimToNull(llmResult.getQuestionText()) != null) {
            appendAssistantTurn(session, llmResult.getQuestionText().trim());
            session.setTurnCount(session.getTurnCount() + 1);
            sessionRepository.save(session);
            return AiDoctorClarifyResponse.builder()
                    .diagnosisId(session.getDiagnosisId())
                    .type("QUESTION")
                    .message(llmResult.getQuestionText().trim())
                    .turnsUsed(session.getTurnCount())
                    .build();
        }

        // responseType thiếu/không hợp lệ hoặc QUESTION rỗng — không đoán mò, escalate.
        log.warn("[AiDoctorClarify] diagnosisId={} Gemini tra ve output khong hop le: responseType={}",
                session.getDiagnosisId(), responseType);
        escalate(session);
        return toResponse(session);
    }

    private Optional<AiClarifyCandidateSummary> validateDecision(AiDoctorClarifySession session, String diseaseCode) {
        String normalized = trimToNull(diseaseCode);
        if (normalized == null || !readCandidateCodes(session).contains(normalized)) {
            return Optional.empty();
        }
        // Guardrail cuối: bệnh đó phải vẫn đang APPROVED tại thời điểm chốt, không chỉ dựa vào snapshot lúc bắt đầu phiên.
        return aiKnowledgeService.findApprovedCandidate(normalized);
    }

    private AiDoctorClarifyResponse finalizeDecision(AiDoctorClarifySession session, AiClarifyCandidateSummary candidate) {
        AiDoctorDiagnosisResponse fullDiagnosis;
        try {
            // Build TRƯỚC khi chốt trạng thái DECIDED: nếu bệnh vừa bị gỡ duyệt đúng lúc này
            // (đụng độ hiếm với thao tác Admin), escalate() thay vì để NotFoundException văng
            // uncaught ở lượt cuối cùng của cả cuộc hội thoại.
            fullDiagnosis = buildFinalDiagnosis(session, candidate);
        } catch (Exception ex) {
            log.warn("[AiDoctorClarify] diagnosisId={} khong lay duoc phac do cho diseaseCode={} (co the vua bi go duyet), escalate: {}",
                    session.getDiagnosisId(), candidate.getDiseaseCode(), ex.getMessage());
            escalate(session);
            return toResponse(session);
        }

        session.setStatus(AiClarifySessionStatus.DECIDED);
        session.setDecidedDiseaseCode(candidate.getDiseaseCode());
        sessionRepository.save(session);

        if (session.getDiagnosisHistoryId() != null) {
            try {
                historyService.updateWithClarifiedDisease(
                        session.getDiagnosisHistoryId(),
                        fullDiagnosis.getDisease(),
                        fullDiagnosis.getCauses(),
                        fullDiagnosis.getSignsSummary(),
                        fullDiagnosis.getTreatmentStages());
            } catch (Exception ex) {
                log.warn("[AiDoctorClarify] cap nhat history that bai (graceful): historyId={}, error={}",
                        session.getDiagnosisHistoryId(), ex.getMessage());
            }
        }

        log.info("[AiDoctorClarify] diagnosisId={} DECIDED: diseaseCode={}, turns={}",
                session.getDiagnosisId(), candidate.getDiseaseCode(), session.getTurnCount());

        return AiDoctorClarifyResponse.builder()
                .diagnosisId(session.getDiagnosisId())
                .type("DECISION")
                .message("Bác sĩ đã xác định được bệnh, bà con xem kết quả bên dưới nhé.")
                .diagnosis(fullDiagnosis)
                .turnsUsed(session.getTurnCount())
                .build();
    }

    private AiDoctorDiagnosisResponse buildFinalDiagnosis(AiDoctorClarifySession session, AiClarifyCandidateSummary candidate) {
        AiDoctorDiagnosisResponse prescription = aiKnowledgeService.buildPrescriptionFromApprovedKnowledge(
                candidate.getDiseaseCode(), session.getDiagnosisHistoryId());

        return AiDoctorDiagnosisResponse.builder()
                .diagnosisId(session.getDiagnosisId())
                .status("DISEASE")
                .imageUrl(session.getImageUrl())
                .disease(DiseaseResponse.builder()
                        .code(candidate.getDiseaseCode())
                        .nameVi(candidate.getNameVi())
                        .nameEn(candidate.getNameEn())
                        .build())
                .causes(prescription.getCauses())
                .signsSummary(prescription.getSignsSummary())
                .treatmentStages(prescription.getTreatmentStages())
                // Đã chốt bệnh qua hỏi-đáp — không còn "đang chờ xác nhận" nữa, khai báo tường
                // minh (không chỉ dựa vào việc bỏ trống field) để mọi client đều hiểu đúng.
                .needsClarification(false)
                .build();
    }

    private void escalate(AiDoctorClarifySession session) {
        List<AiClarifyTurn> turns = readTurns(session);
        String lastFarmerText = turns.stream()
                .filter(turn -> AiClarifyTurn.ROLE_FARMER.equals(turn.getRole()))
                .reduce((first, second) -> second)
                .map(AiClarifyTurn::getText)
                .orElse(null);

        AiKnowledgeReviewCase reviewCase = aiKnowledgeService.createReviewCase(
                session.getUserId(),
                session.getDiagnosisId(),
                session.getSourceChannel(),
                lastFarmerText,
                session.getInitialSymptoms(),
                session.getImageUrl(),
                session.getAiSuggestedDiseaseCode(),
                null,
                0D,
                AiReviewCaseReason.LOW_CONFIDENCE);

        session.setStatus(AiClarifySessionStatus.ESCALATED);
        session.setReviewCaseId(reviewCase.getId());
        sessionRepository.save(session);
    }

    private AiDoctorClarifyResponse toResponse(AiDoctorClarifySession session) {
        if (session.getStatus() == AiClarifySessionStatus.DECIDED) {
            AiClarifyCandidateSummary candidate = aiKnowledgeService.findApprovedCandidate(session.getDecidedDiseaseCode())
                    .orElseGet(() -> AiClarifyCandidateSummary.builder()
                            .diseaseCode(session.getDecidedDiseaseCode())
                            .build());
            try {
                AiDoctorDiagnosisResponse fullDiagnosis = buildFinalDiagnosis(session, candidate);
                return AiDoctorClarifyResponse.builder()
                        .diagnosisId(session.getDiagnosisId())
                        .type("DECISION")
                        .message("Bác sĩ đã xác định được bệnh, bà con xem kết quả bên dưới nhé.")
                        .diagnosis(fullDiagnosis)
                        .turnsUsed(session.getTurnCount())
                        .build();
            } catch (Exception ex) {
                // Bệnh đã chốt trước đó có thể vừa bị gỡ duyệt — vẫn phải trả lời được thay vì
                // để request replay/idempotent này 500, dù không dựng lại được phác đồ đầy đủ.
                log.warn("[AiDoctorClarify] khong the dung lai ket qua da chot: diagnosisId={}, diseaseCode={}, error={}",
                        session.getDiagnosisId(), session.getDecidedDiseaseCode(), ex.getMessage());
                return AiDoctorClarifyResponse.builder()
                        .diagnosisId(session.getDiagnosisId())
                        .type("DECISION")
                        .message("Bác sĩ đã xác định được bệnh trước đó. Bà con vui lòng xem lại trong Sổ khám bệnh để biết chi tiết.")
                        .turnsUsed(session.getTurnCount())
                        .build();
            }
        }

        return AiDoctorClarifyResponse.builder()
                .diagnosisId(session.getDiagnosisId())
                .type("ESCALATED")
                .message(ESCALATION_MESSAGE)
                .turnsUsed(session.getTurnCount())
                .build();
    }

    private AiDoctorClarifyResponse replayLastQuestion(AiDoctorClarifySession session, List<AiClarifyTurn> turns) {
        String lastQuestion = turns.stream()
                .filter(turn -> AiClarifyTurn.ROLE_ASSISTANT.equals(turn.getRole()))
                .reduce((first, second) -> second)
                .map(AiClarifyTurn::getText)
                .orElse(null);

        if (lastQuestion == null) {
            // Trạng thái bất thường (không tìm được câu hỏi cũ) — an toàn nhất là hỏi lại Gemini
            // thay vì trả về một response rỗng.
            return callGeminiAndAdvance(session);
        }

        return AiDoctorClarifyResponse.builder()
                .diagnosisId(session.getDiagnosisId())
                .type("QUESTION")
                .message(lastQuestion)
                .turnsUsed(session.getTurnCount())
                .build();
    }

    // =========================================================
    // PRIVATE — conversation persistence helpers
    // =========================================================

    private void appendFarmerTurn(AiDoctorClarifySession session, String answer) {
        String text = trimToNull(answer);
        if (text == null) {
            return; // lượt bootstrap thường không kèm answer
        }
        List<AiClarifyTurn> turns = new ArrayList<>(readTurns(session));
        turns.add(AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_FARMER).text(text).build());
        session.setConversationJson(writeJson(turns));
    }

    private void appendAssistantTurn(AiDoctorClarifySession session, String text) {
        List<AiClarifyTurn> turns = new ArrayList<>(readTurns(session));
        turns.add(AiClarifyTurn.builder().role(AiClarifyTurn.ROLE_ASSISTANT).text(text).build());
        session.setConversationJson(writeJson(turns));
    }

    private List<AiClarifyTurn> readTurns(AiDoctorClarifySession session) {
        List<AiClarifyTurn> turns = readJson(session.getConversationJson(), new TypeReference<List<AiClarifyTurn>>() {
        });
        return turns != null ? turns : Collections.emptyList();
    }

    private List<AiClarifyCandidateSummary> readCandidates(AiDoctorClarifySession session) {
        // Re-resolve từ snapshot đã duyệt hiện tại (không cache cứng trong entity) để câu hỏi luôn dựa trên
        // dữ liệu mới nhất — nhưng KHÔNG mở rộng tập, chỉ dùng đúng các mã đã khoá lúc bắt đầu phiên.
        return aiKnowledgeService.resolveApprovedCandidates(readCandidateCodes(session));
    }

    private List<String> readCandidateCodes(AiDoctorClarifySession session) {
        List<String> codes = readJson(session.getCandidateDiseaseCodesJson(), new TypeReference<List<String>>() {
        });
        return codes != null ? codes : Collections.emptyList();
    }

    private <T> T readJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ex) {
            log.warn("[AiDoctorClarify] khong doc duoc JSON hoi-dap: {}", ex.getMessage());
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("Khong the luu JSON hoi-dap AI Doctor", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}

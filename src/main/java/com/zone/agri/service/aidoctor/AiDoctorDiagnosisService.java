package com.zone.agri.service.aidoctor;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.client.ai.AiDiagnosisClient;
import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.ai.AiImageNarrativeResult;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Orchestration chính cho luồng chẩn đoán AI Doctor:
 *  Bước 1 — POST /diagnosis:      Validate image → YOLO predict → lưu history → trả kết quả bệnh
 *  Bước 2 — POST /diagnosis/{id}/prescription: lấy phác đồ từ kho tri thức đã duyệt → cập nhật history → trả phác đồ
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
    private final GeminiClarifyClient geminiClarifyClient;

    private static final long MAX_IMAGE_SIZE_BYTES = 5_000_000L;
    private static final int MAX_SYMPTOMS_LENGTH = 500;

    // Nam duoi read-timeout rieng cua Gemini (45s) de con du gio cho YOLO + Cloudinary trong tran
    // 90s timeout /diagnosis phia FE — khong de mot cuoc goi Gemini cham lam FE tu bo request du
    // BE cuoi cung van se tra ket qua dung.
    @Value("${gemini.narrative-join-timeout-seconds:20}")
    private int narrativeJoinTimeoutSeconds;

    public AiDoctorDiagnosisResponse diagnose(MultipartFile image, String userSymptoms, Long userId) {
        return diagnose(image, userSymptoms, userId, null);
    }

    public AiDoctorDiagnosisResponse diagnose(MultipartFile image, String userSymptoms, Long userId, String sessionId) {
        return diagnoseInternal(image, userSymptoms, userId, sessionId, null, true);
    }

    /**
     * Danh cho trang "Chat thu nghiem" — goi DUNG luong that ben duoi (YOLO + Gemini that, cung
     * enrichDiagnosis), chi khac: userId luon null (khong luu history/So kham), khong tao review
     * case that (allowReviewCase=false, tranh lam nhieu hang doi "Cau hoi chua dap"), va cho phep
     * xem truoc 1 phac do chua duyet qua previewDiseaseCode.
     */
    public AiDoctorDiagnosisResponse diagnoseForTest(MultipartFile image, String userSymptoms, String previewDiseaseCode) {
        return diagnoseInternal(image, userSymptoms, null, null, previewDiseaseCode, false);
    }

    private AiDoctorDiagnosisResponse diagnoseInternal(MultipartFile image, String userSymptoms, Long userId,
            String sessionId, String previewDiseaseCode, boolean allowReviewCase) {

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

        // 1a. Bat song song mo ta anh tu Gemini ngay tu dau (doc bytes 1 lan tren thread hien tai
        // roi truyen byte[] thuan vao task async — khong doc MultipartFile tu thread khac, vi
        // AiDiagnosisClient.predict(image) cung dang doc chinh MultipartFile nay dong thoi ben duoi).
        CompletableFuture<AiImageNarrativeResult> narrativeFuture = kickOffNarrativeDescription(image, normalizedSymptoms, traceId);

        // 2. AI predict-image (fail → toàn bộ request fail, không graceful)
        AiPredictResponse predictResponse = aiDiagnosisClient.predict(image);
        String aiStatus = predictResponse.getStatus();
        String diagnosisImageUrl = uploadAnnotatedImageGracefully(image, predictResponse, traceId);

        // 2a. Xử lý sớm các trạng thái đặc biệt từ AI
        if ("BLURRY".equals(aiStatus)) {
            log.info("[AiDoctor] traceId={} BLURRY image", traceId);
            throw new BadRequestException(
                    "Ảnh quá mờ hoặc chất lượng thấp. Vui lòng chụp lại ảnh rõ hơn.");
        }

        // Doi Gemini 1 lan duy nhat, dung chung cho moi nhanh con lai (khac BLURRY, khong can Gemini).
        // YOLO chi duoc train phan biet 5 lop TRONG PHAM VI tom, khong co lop "khong phai tom" —
        // gap anh la, cho, meo... no van co gang gan vao 1 trong 5 lop da biet (thuong roi vao
        // Healthy vi day la lop "khong co dau hieu ton thuong ro" trong du lieu train). Gemini duoc
        // hoi rieng "day co phai la con tom khong" de lam luoi an toan bat truong hop nay. Neu Gemini
        // fail/timeout, narrative=null va GIU NGUYEN ket qua YOLO nhu cu (fail-open, khong regress).
        AiImageNarrativeResult narrative = resolveNarrativeGracefully(narrativeFuture, traceId);
        String narrativeText = narrative != null ? narrative.getDescription() : null;
        boolean geminiConfirmsNotShrimp = narrative != null && !narrative.isShrimp();

        if ("NON_SHRIMP".equals(aiStatus) || geminiConfirmsNotShrimp) {
            log.info("[AiDoctor] traceId={} NON_SHRIMP image (yoloStatus={}, geminiConfirmsNotShrimp={}), "
                    + "tra loi than thien bang mo ta cua Gemini thay vi bao loi chung chung",
                    traceId, aiStatus, geminiConfirmsNotShrimp);
            AiDoctorDiagnosisResponse response = aiKnowledgeService.buildNonShrimpImageResponse(
                    predictResponse,
                    diagnosisImageUrl,
                    normalizedSymptoms,
                    sessionId != null ? sessionId : "diag_" + traceId,
                    userId,
                    narrativeText,
                    allowReviewCase);
            saveHistoryGracefully(response, userId, normalizedSymptoms, traceId);
            return response;
        }
        if ("HEALTHY".equals(aiStatus)) {
            log.info("[AiDoctor] traceId={} HEALTHY shrimp", traceId);
            AiDoctorDiagnosisResponse response = AiDoctorDiagnosisResponse.builder()
                    .diagnosisId("healthy_" + traceId)
                    .status("HEALTHY")
                    .imageUrl(diagnosisImageUrl)
                    .build();
            saveHistoryGracefully(response, userId, normalizedSymptoms, traceId);
            return response;
        }

        AiPredictionItem finalPrediction = predictResponse.getFinalPrediction();

        if (finalPrediction == null || finalPrediction.getDiseaseCode() == null) {
            log.warn("[AiDoctor] traceId={} AI khong nhan ra benh, chuyen sang goi y Gemini tu do", traceId);
            AiDoctorDiagnosisResponse response = aiKnowledgeService.buildUnrecognizedDiagnosisResponse(
                    predictResponse,
                    finalPrediction,
                    diagnosisImageUrl,
                    normalizedSymptoms,
                    sessionId != null ? sessionId : "diag_" + traceId,
                    userId,
                    narrativeText,
                    allowReviewCase);
            saveHistoryGracefully(response, userId, normalizedSymptoms, traceId);
            return response;
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
                userId,
                narrativeText,
                previewDiseaseCode,
                allowReviewCase);

        // Luu history cho MOI outcome (khong chi DISEASE nua) — enrichDiagnosis co the tu tra ve
        // UNRECOGNIZED ben trong no (nhanh confidence/match khong dat nguong), van can luu de phat
        // lai duoc trong "So kham"/khoi phuc chat hom nay.
        saveHistoryGracefully(response, userId, normalizedSymptoms, traceId);

        log.info("[AiDoctor] traceId={} done: userId={}, diagnosisId={}", traceId, userId, response.getDiagnosisId());
        return response;
    }

    /**
     * Luu 1 ban ghi history cho BAT KY outcome nao (DISEASE/HEALTHY/UNRECOGNIZED) khi user da dang
     * nhap — de "So kham"/khoi phuc chat hom nay co du lieu phat lai. Guest (userId null) khong
     * luu, giong hanh vi cu.
     */
    private void saveHistoryGracefully(AiDoctorDiagnosisResponse response, Long userId, String userSymptoms, String traceId) {
        if (userId == null) return;
        try {
            Long historyId = diagnosisHistoryService.saveDiagnosisHistory(response, userId, userSymptoms);
            response.setDiagnosisId(String.valueOf(historyId));
        } catch (Exception e) {
            log.error("[AiDoctor] traceId={} History save fail (graceful): {}", traceId, e.getMessage());
        }
    }

    // =========================================================
    // PUBLIC: Lấy phác đồ từ kho tri thức đã duyệt (bước 2 — user chủ động trigger)
    // =========================================================

    public AiDoctorDiagnosisResponse generatePrescription(Long diagnosisId, Long userId) {
        AiDoctorDiagnosisHistory history = historyRepository.findByIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new NotFoundException("AI_DOCTOR_DIAGNOSIS_NOT_FOUND"));

        // Ca này còn đang chờ AI Doctor hỏi làm rõ bệnh (chưa xác nhận) — finalDiseaseCode
        // hiện tại chỉ là dự đoán YOLO độ tin cậy thấp, không được phép tạo phác đồ cho nó.
        if (Boolean.TRUE.equals(history.getNeedsClarification())) {
            throw new BadRequestException(
                    "Ca chẩn đoán này đang chờ bác sĩ AI hỏi thêm để xác nhận bệnh. Vui lòng hoàn tất hội thoại trước khi xem phác đồ.");
        }

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

    private CompletableFuture<AiImageNarrativeResult> kickOffNarrativeDescription(MultipartFile image, String userSymptoms, String traceId) {
        byte[] imageBytes;
        String contentType;
        try {
            imageBytes = image.getBytes();
            contentType = image.getContentType();
        } catch (Exception e) {
            log.warn("[AiDoctor] traceId={} doc bytes anh cho Gemini narrative fail (graceful): {}",
                    traceId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }

        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = (contentType != null && !contentType.isBlank()) ? contentType : "image/jpeg";
        return CompletableFuture.supplyAsync(
                () -> geminiClarifyClient.describeImage(userSymptoms, imageBase64, mimeType));
    }

    private AiImageNarrativeResult resolveNarrativeGracefully(CompletableFuture<AiImageNarrativeResult> narrativeFuture, String traceId) {
        try {
            return narrativeFuture.get(narrativeJoinTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[AiDoctor] traceId={} Gemini narrative fail/timeout (graceful): {}",
                    traceId, e.getMessage());
            return null;
        }
    }

    /**
     * Uu tien upload anh DA ANNOTATE (co box YOLO) tu AI service. Neu AI service khong tra
     * annotatedImage (khong phai luc nao cung co) hoac Cloudinary loi, fallback sang upload ANH
     * GOC nguoi dung gui — dam bao imageUrl gan nhu luon co de "So kham"/khoi phuc chat con anh de
     * phat lai, thay vi de trong null nhu truoc (anh chi song sot qua clientImageUrl phia FE, mat
     * han sau khi rong trang vi cache do khong bao gio gui len BE).
     */
    private String uploadAnnotatedImageGracefully(MultipartFile image, AiPredictResponse predictResponse, String traceId) {
        String annotatedImage = predictResponse.getAnnotatedImage();
        if (annotatedImage != null && !annotatedImage.isBlank()) {
            try {
                return cloudinaryService.uploadImage(annotatedImage, "ai-doctor/diagnosis");
            } catch (Exception e) {
                log.warn("[AiDoctor] traceId={} upload annotated image fail, fallback anh goc (graceful): {}",
                        traceId, e.getMessage());
            }
        }

        try {
            return cloudinaryService.upload(image, "ai-doctor/diagnosis").secureUrl();
        } catch (Exception e) {
            log.warn("[AiDoctor] traceId={} upload anh goc fail (graceful): {}", traceId, e.getMessage());
            return null;
        }
    }
}

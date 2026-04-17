package com.zone.agri.service.miniapp;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.client.ai.AiDiagnosisClient;
import com.zone.agri.client.ai.AiPrescriptionClient;
import com.zone.agri.dto.miniapp.ai.*;
import com.zone.agri.dto.miniapp.response.*;
import com.zone.agri.entity.MiniAppDiagnosisHistory;
import com.zone.agri.entity.Product;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.MiniAppDiagnosisHistoryRepository;
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
 * Orchestration chính cho luồng chẩn đoán Mini App:
 *  Bước 1 — POST /diagnosis:      Validate image → YOLO predict → lưu history → trả kết quả bệnh
 *  Bước 2 — POST /diagnosis/{id}/prescription: Gemini generate-prescription → cập nhật history → trả phác đồ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MiniAppDiagnosisService {

    private final CloudinaryService cloudinaryService;
    private final AiDiagnosisClient aiDiagnosisClient;
    private final AiPrescriptionClient aiPrescriptionClient;
    private final MiniAppProductSuggestionService productSuggestionService;
    private final MiniAppDiagnosisHistoryService diagnosisHistoryService;
    private final MiniAppDiagnosisHistoryRepository historyRepository;

    private static final long MAX_IMAGE_SIZE_BYTES = 5_000_000L;
    private static final int MAX_SYMPTOMS_LENGTH = 500;

    public MiniAppDiagnosisResponse diagnose(MultipartFile image, String userSymptoms, Long userId) {

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

        log.info("[MiniApp] traceId={} start: userId={}, file={}, symptomsLen={}",
                traceId, userId, image.getOriginalFilename(), normalizedSymptoms.length());

        // 2. AI predict-image (fail → toàn bộ request fail, không graceful)
        AiPredictResponse predictResponse = aiDiagnosisClient.predict(image);
        String aiStatus = predictResponse.getStatus();
        String diagnosisImageUrl = uploadAnnotatedImageGracefully(predictResponse, traceId);

        // 2a. Xử lý sớm các trạng thái đặc biệt từ AI
        if ("BLURRY".equals(aiStatus)) {
            log.info("[MiniApp] traceId={} BLURRY image", traceId);
            throw new BadRequestException(
                    "Ảnh quá mờ hoặc chất lượng thấp. Vui lòng chụp lại ảnh rõ hơn.");
        }
        if ("NON_SHRIMP".equals(aiStatus)) {
            log.info("[MiniApp] traceId={} NON_SHRIMP image", traceId);
            throw new BadRequestException(
                    "Không phát hiện tôm trong ảnh. Vui lòng điều chỉnh góc chụp và thử lại.");
        }
        if ("HEALTHY".equals(aiStatus)) {
            log.info("[MiniApp] traceId={} HEALTHY shrimp", traceId);
            return MiniAppDiagnosisResponse.builder()
                    .diagnosisId("healthy_" + traceId)
                    .status("HEALTHY")
                    .imageUrl(diagnosisImageUrl)
                    .build();
        }

        AiPredictionItem finalPrediction = predictResponse.getFinalPrediction();

        if (finalPrediction == null || finalPrediction.getDiseaseCode() == null) {
            log.warn("[MiniApp] traceId={} AI không nhận ra bệnh", traceId);
            throw new BadRequestException(
                    "Không thể nhận dạng bệnh từ ảnh. Vui lòng chụp ảnh rõ hơn và thử lại.");
        }

        String diseaseCode = finalPrediction.getDiseaseCode();
        log.info("[MiniApp] traceId={} predict OK: diseaseCode={}, confidence={}%",
                traceId, diseaseCode, finalPrediction.getConfidencePercent());

        // 3. Build response chỉ với kết quả bệnh (không có prescription)
        MiniAppDiagnosisResponse response = buildDiseaseOnlyResponse(
                predictResponse, finalPrediction, diagnosisImageUrl);

        // 4. Lưu history ngay — prescription sẽ được cập nhật sau khi user chủ động gọi
        try {
            Long historyId = diagnosisHistoryService.saveDiagnosisHistory(response, userId, normalizedSymptoms);
            response.setDiagnosisId(String.valueOf(historyId));
        } catch (Exception e) {
            log.error("[MiniApp] traceId={} History save fail (graceful): {}", traceId, e.getMessage());
        }

        log.info("[MiniApp] traceId={} done: userId={}, diagnosisId={}", traceId, userId, response.getDiagnosisId());
        return response;
    }

    // =========================================================
    // PUBLIC: Gọi Gemini tạo phác đồ (bước 2 — user chủ động trigger)
    // =========================================================

    public MiniAppDiagnosisResponse generatePrescription(Long diagnosisId, Long userId) {
        MiniAppDiagnosisHistory history = historyRepository.findByIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new NotFoundException("MINIAPP_DIAGNOSIS_NOT_FOUND"));

        String diseaseCode = history.getFinalDiseaseCode();
        if (diseaseCode == null) {
            throw new BadRequestException("Không thể tạo phác đồ cho ca chẩn đoán này");
        }

        // Re-fetch candidate products theo diseaseCode
        List<Product> candidateProducts = productSuggestionService.getCandidateProductsForDisease(diseaseCode);
        List<AiStockItem> availableStock = productSuggestionService.toAiStockItems(candidateProducts);
        Map<Long, Product> productMap = productSuggestionService.toProductMap(candidateProducts);
        Map<Long, Long> priceMap = productSuggestionService.getPriceMap(candidateProducts);

        AiPrescriptionRequest request = AiPrescriptionRequest.builder()
                .diseaseCode(diseaseCode)
                .diseaseName(history.getFinalDiseaseNameVi())
                .userSymptoms(history.getUserSymptoms() != null ? history.getUserSymptoms() : "")
                .availableStock(availableStock)
                .build();

        AiPrescriptionResponse prescriptionResponse = aiPrescriptionClient.generatePrescription(request);

        // Build treatment stages với product mapping
        List<String> causes = prescriptionResponse.getCauses() != null
                ? prescriptionResponse.getCauses() : Collections.emptyList();
        String signsSummary = prescriptionResponse.getSignsSummary();
        List<TreatmentStageResponse> treatmentStages = Collections.emptyList();
        if (prescriptionResponse.getTreatmentStages() != null) {
            treatmentStages = prescriptionResponse.getTreatmentStages().stream()
                    .map(stage -> TreatmentStageResponse.builder()
                            .stageTitle(stage.getStageTitle())
                            .instructions(stage.getInstructions() != null
                                    ? stage.getInstructions() : Collections.emptyList())
                            .products(productSuggestionService.mapRelatedProductIdsToSuggestedProducts(
                                    stage.getRelatedProductIds(), productMap, priceMap))
                            .build())
                    .collect(Collectors.toList());
        }

        // Cập nhật history với prescription data
        diagnosisHistoryService.updateWithPrescription(diagnosisId, causes, signsSummary, treatmentStages);

        log.info("[MiniApp-Prescription] diagnosisId={}, diseaseCode={}, stages={}",
                diagnosisId, diseaseCode, treatmentStages.size());

        return MiniAppDiagnosisResponse.builder()
                .diagnosisId(String.valueOf(diagnosisId))
                .causes(causes)
                .signsSummary(signsSummary)
                .treatmentStages(treatmentStages)
                .build();
    }

    // =========================================================
    // PRIVATE: Build response chỉ với kết quả bệnh (không prescription)
    // =========================================================

    private MiniAppDiagnosisResponse buildDiseaseOnlyResponse(
            AiPredictResponse predict,
            AiPredictionItem finalPrediction,
            String diagnosisImageUrl) {

        DiseaseResponse disease = DiseaseResponse.builder()
                .code(finalPrediction.getDiseaseCode())
                .nameVi(finalPrediction.getVietnameseName())
                .nameEn(finalPrediction.getEnglishName())
                .confidencePercent(finalPrediction.getConfidencePercent())
                .build();

        List<TopPredictionResponse> topPredictions = (predict.getTopPredictions() != null)
                ? predict.getTopPredictions().stream()
                        .map(p -> TopPredictionResponse.builder()
                                .diseaseCode(p.getDiseaseCode())
                                .nameVi(p.getVietnameseName())
                                .nameEn(p.getEnglishName())
                                .confidencePercent(p.getConfidencePercent())
                                .build())
                        .collect(Collectors.toList())
                : Collections.emptyList();

        String diagnosisId = "diag_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        return MiniAppDiagnosisResponse.builder()
                .diagnosisId(diagnosisId)
                .status("DISEASE")
                .imageUrl(diagnosisImageUrl)
                .disease(disease)
                .topPredictions(topPredictions)
                .build();
    }

    private String uploadAnnotatedImageGracefully(AiPredictResponse predictResponse, String traceId) {
        String annotatedImage = predictResponse.getAnnotatedImage();
        if (annotatedImage == null || annotatedImage.isBlank()) {
            return null;
        }

        try {
            return cloudinaryService.uploadImage(annotatedImage, "miniapp/diagnosis");
        } catch (Exception e) {
            log.warn("[MiniApp] traceId={} upload annotated image fail (graceful): {}",
                    traceId, e.getMessage());
            return null;
        }
    }
}

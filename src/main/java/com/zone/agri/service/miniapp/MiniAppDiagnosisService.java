package com.zone.agri.service.miniapp;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.client.ai.AiDiagnosisClient;
import com.zone.agri.client.ai.AiPrescriptionClient;
import com.zone.agri.dto.miniapp.ai.*;
import com.zone.agri.dto.miniapp.response.*;
import com.zone.agri.entity.Product;
import com.zone.agri.exception.BadRequestException;
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
 *  1. Validate image
 *  2. AI predict-image → disease_code
 *  3. Chọn candidate products đã xếp hạng (Phase BE-3)
 *  4. AI generate-prescription (graceful degradation nếu fail)
 *  5. Map + clamp AI product ids → SuggestedProductResponse
 *  6. Build response chuẩn cho FE
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

        // 3. Phase BE-3: candidate products đã xếp hạng + price map (1 batch query)
        List<Product> candidateProducts = productSuggestionService.getCandidateProductsForDisease(diseaseCode);
        List<AiStockItem> availableStock = productSuggestionService.toAiStockItems(candidateProducts);
        Map<Long, Product> productMap = productSuggestionService.toProductMap(candidateProducts);
        Map<Long, Long> priceMap = productSuggestionService.getPriceMap(candidateProducts);

        log.debug("[MiniApp] traceId={} candidateProducts={}, stockItems={}",
                traceId, candidateProducts.size(), availableStock.size());

        // 4. Build AI prescription request
        // idealProtocol không cần truyền — Python AI tự lookup từ Knowledge Base nội bộ
        AiPrescriptionRequest prescriptionRequest = AiPrescriptionRequest.builder()
                .diseaseCode(diseaseCode)
                .diseaseName(finalPrediction.getVietnameseName())
                .userSymptoms(normalizedSymptoms)
                .availableStock(availableStock)
                .build();

        // 5. AI generate-prescription — graceful: fail không block diagnosis result
        AiPrescriptionResponse prescriptionResponse = null;
        try {
            prescriptionResponse = aiPrescriptionClient.generatePrescription(prescriptionRequest);
        } catch (Exception e) {
            log.warn("[MiniApp] traceId={} prescription fail (graceful): diseaseCode={}, reason={}",
                    traceId, diseaseCode, e.getMessage());
        }

        // 6. Build response (diagnosisId tạm — sẽ được thay bằng DB id ở bước sau)
        MiniAppDiagnosisResponse response = buildResponse(
                predictResponse, finalPrediction, prescriptionResponse, productMap, priceMap, diagnosisImageUrl);

        // 7. Phase BE-4: lưu history + gắn real DB id vào diagnosisId
        // Graceful: nếu save fail vẫn trả diagnosis cho FE, log lỗi để fix sau.
        // diagnosisId giữ giá trị tạm nếu save fail (FE vẫn nhận đủ kết quả,
        // chỉ mất khả năng reload từ history).
        try {
            Long historyId = diagnosisHistoryService.saveDiagnosisHistory(response, userId, normalizedSymptoms);
            response.setDiagnosisId(String.valueOf(historyId));
        } catch (Exception e) {
            log.error("[MiniApp] traceId={} History save fail (graceful — diagnosis vẫn trả về FE): {}",
                    traceId, e.getMessage());
        }

        log.info("[MiniApp] traceId={} done: userId={}, diagnosisId={}", traceId, userId, response.getDiagnosisId());
        return response;
    }

    // =========================================================
    // PRIVATE: Build response từ AI results
    // =========================================================

    private MiniAppDiagnosisResponse buildResponse(
            AiPredictResponse predict,
            AiPredictionItem finalPrediction,
            AiPrescriptionResponse prescription,
            Map<Long, Product> productMap,
            Map<Long, Long> priceMap,
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

        // Graceful defaults khi prescription null
        List<String> causes = Collections.emptyList();
        String signsSummary = null;
        List<TreatmentStageResponse> treatmentStages = Collections.emptyList();

        if (prescription != null) {
            if (prescription.getCauses() != null) {
                causes = prescription.getCauses();
            }
            signsSummary = prescription.getSignsSummary();
            if (prescription.getTreatmentStages() != null) {
                treatmentStages = prescription.getTreatmentStages().stream()
                        .map(stage -> TreatmentStageResponse.builder()
                                .stageTitle(stage.getStageTitle())
                                .instructions(stage.getInstructions() != null
                                        ? stage.getInstructions() : Collections.emptyList())
                                // Phase BE-3: clamp + dedup AI product ids
                                .products(productSuggestionService.mapRelatedProductIdsToSuggestedProducts(
                                        stage.getRelatedProductIds(), productMap, priceMap))
                                .build())
                        .collect(Collectors.toList());
            }
        }

        String diagnosisId = "diag_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        return MiniAppDiagnosisResponse.builder()
                .diagnosisId(diagnosisId)
                .status("DISEASE")
                .imageUrl(diagnosisImageUrl)
                .disease(disease)
                .topPredictions(topPredictions)
                .causes(causes)
                .signsSummary(signsSummary)
                .treatmentStages(treatmentStages)
                .purchaseUrl(null)       // Phase BE-4: purchase link
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

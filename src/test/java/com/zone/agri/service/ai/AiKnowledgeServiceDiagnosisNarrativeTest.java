package com.zone.agri.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.ai.AiPredictResponse;
import com.zone.agri.dto.ai.AiPredictionItem;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiKnowledgeReviewCase;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.entity.enums.AiReviewCaseReason;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiKnowledgeReviewCaseRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test có chủ đích cho phần narrative mới ghép vào luồng chẩn đoán ảnh (YOLO) —
 * {@code AiKnowledgeService#enrichDiagnosis}: mô tả ảnh của Gemini (nhận vào dưới dạng String đã
 * resolve sẵn, không tự gọi Gemini trong hàm này) + câu trích dẫn %/tên bệnh do code tự ghép +
 * nội dung an toàn đã có sẵn (phác đồ đã duyệt hoặc câu chuyển tiếp tĩnh).
 *
 * Chạy thật với DB H2 trong bộ nhớ (không cần Docker), mock GeminiClarifyClient chỉ để đảm bảo
 * không có lời gọi HTTP thật nào lọt ra ngoài — bản thân enrichDiagnosis không gọi Gemini trực
 * tiếp (tham số geminiImageDescription đã được AiDoctorDiagnosisService resolve từ trước).
 *
 * Đồng thời đóng vai trò baseline test cho logic ngưỡng confidence/match score trong
 * enrichDiagnosis — trước bản đổi này CHƯA có test nào bao phủ hàm này.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "security.jwt.secret-key=test-secret-key-for-jwt-util-in-test",
    "security.jwt.issuer=test-issuer",
    "security.jwt.expiry-time-in-seconds=86400",
    "security.jwt.refreshable-duration=86400",
    "mnl.tmp-dir=mnt/",
    "spring.datasource.url=jdbc:h2:mem:agri-diagnosis-narrative-test;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.startup.schema-patches.enabled=false",
    "app.startup.seed-data.enabled=false",
    "spring.data.redis.repositories.enabled=false",
    "ai.base-url=http://localhost:8000",
    "ai.doctor.clarify.max-turns=8"
})
@Transactional
class AiKnowledgeServiceDiagnosisNarrativeTest {

    @Autowired
    private AiKnowledgeService aiKnowledgeService;

    @Autowired
    private AiDiseaseKnowledgeRepository diseaseKnowledgeRepository;

    @Autowired
    private AiKnowledgeReviewCaseRepository reviewCaseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeminiClarifyClient geminiClarifyClient;

    @BeforeEach
    void resetServiceState() {
        ReflectionTestUtils.invokeMethod(aiKnowledgeService, "evictApprovedSnapshot");
    }

    private AiDiseaseKnowledge seedDiseaseWithProtocol(String code, String nameVi, String symptomKeyword,
            double matchThreshold, double confidenceThreshold) throws Exception {
        String causesJson = objectMapper.writeValueAsString(List.of("Moi truong bien dong", "Mat do nuoi qua cao"));
        String stagesJson = objectMapper.writeValueAsString(List.of(Map.of(
                "stageTitle", "Giai doan 1: On dinh moi truong",
                "instructions", List.of("Giam soc cho tom", "Tang cuong oxy hoa tan"),
                "productIds", List.of(),
                "extraProductNames", List.of("Vi sinh xu ly day ao"))));
        return diseaseKnowledgeRepository.save(AiDiseaseKnowledge.builder()
                .code(code)
                .nameVi(nameVi)
                .symptomKeywordsRaw(symptomKeyword)
                .signsSummary("Dau hieu dac trung cua " + nameVi)
                .causesJson(causesJson)
                .treatmentStagesJson(stagesJson)
                .confidenceThreshold(confidenceThreshold)
                .matchThreshold(matchThreshold)
                .enabled(true)
                .priority(0)
                .canonical(false)
                .status(AiKnowledgeStatus.APPROVED)
                .build());
    }

    private AiPredictionItem prediction(String diseaseCode, String nameVi, String nameEn, double confidencePercent) {
        AiPredictionItem item = new AiPredictionItem();
        item.setDiseaseCode(diseaseCode);
        item.setVietnameseName(nameVi);
        item.setEnglishName(nameEn);
        item.setConfidencePercent(confidencePercent);
        return item;
    }

    private AiPredictResponse predictResponse(AiPredictionItem finalPrediction) {
        AiPredictResponse response = new AiPredictResponse();
        response.setSuccess(true);
        response.setStatus("DISEASE");
        response.setFinalPrediction(finalPrediction);
        response.setTopPredictions(List.of(finalPrediction));
        return response;
    }

    // =========================================================
    // 1) Khop cao: mo ta Gemini + trich dan %/ten benh + dung nguyen phac do da duyet
    // =========================================================

    @Test
    void highConfidence_aiDescriptionHasGeminiText_citation_andApprovedTreatment() throws Exception {
        seedDiseaseWithProtocol("DIS_HC", "Benh dom trang test", "khongdungdematch", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_HC", "Benh dom trang test", "White spot test", 92.0D);

        AiDoctorDiagnosisResponse response = aiKnowledgeService.enrichDiagnosis(
                predictResponse(finalPrediction), finalPrediction, "http://img/1.jpg", "",
                "sess-" + System.nanoTime(), 1L, "Minh thay vo tom co dom trang ro rang.");

        assertThat(response.getStatus()).isEqualTo("DISEASE");
        assertThat(response.getNeedsClarification()).isNull();
        assertThat(response.getAiDescription()).contains("Minh thay vo tom co dom trang ro rang.");
        assertThat(response.getAiDescription()).contains("92%");
        assertThat(response.getAiDescription()).contains("Benh dom trang test");
        assertThat(response.getAiDescription()).contains("Giai doan 1: On dinh moi truong");
        assertThat(response.getAiDescription()).contains("Giam soc cho tom");
        assertThat(response.getAiDescription()).contains("Vi sinh xu ly day ao");
    }

    // =========================================================
    // 2) Khop nhung duoi nguong: co mo ta + trich dan, KHONG kem phac do
    // =========================================================

    @Test
    void lowConfidence_aiDescriptionHasCitation_butNoTreatmentContent_needsClarificationTrue() throws Exception {
        seedDiseaseWithProtocol("DIS_LC", "Benh gan tuy test", "khongdungdematch2", 0.6D, 0.65D);
        // confidence 20% duoi ca confidenceThreshold (0.65) lan matchThreshold (0.6, vi diseaseScore
        // ban dau = confidence khi khop truc tiep qua ma benh) -> ca 2 dieu kien deu fail.
        AiPredictionItem finalPrediction = prediction("DIS_LC", "Benh gan tuy test", "Hepatopancreas test", 20.0D);

        AiDoctorDiagnosisResponse response = aiKnowledgeService.enrichDiagnosis(
                predictResponse(finalPrediction), finalPrediction, "http://img/2.jpg", "",
                "sess-" + System.nanoTime(), 1L, "Mau gan tuy nhat hon binh thuong.");

        assertThat(response.getNeedsClarification()).isTrue();
        assertThat(response.getTreatmentStages()).isEmpty();
        assertThat(response.getAiDescription()).contains("Mau gan tuy nhat hon binh thuong.");
        assertThat(response.getAiDescription()).contains("20%");
        assertThat(response.getAiDescription()).contains("Benh gan tuy test");
        // Guardrail chinh cua test nay: khong duoc ro ri noi dung phac do da duyet khi chua du tin cay.
        assertThat(response.getAiDescription()).doesNotContain("Giai doan 1: On dinh moi truong");
        assertThat(response.getAiDescription()).doesNotContain("Giam soc cho tom");
        assertThat(response.getAiDescription()).doesNotContain("Vi sinh xu ly day ao");
    }

    // =========================================================
    // 3) Khong khop bat ky tri thuc nao: khong con la needsClarification chung chung nua — goi
    // Gemini goi y tu do (nhu free-consult) + luon kem lien he ky su, tra ve nhu 1 cau tra loi cuoi.
    // =========================================================

    @Test
    void noKnowledgeMatchAtAll_callsGeminiFreeSuggestion_returnsUnrecognizedStatus() {
        when(geminiClarifyClient.freeConsult(any(), any(), any()))
                .thenReturn("Co the do soc moi truong hoac nhiem khuan ngoai da.");

        AiPredictionItem finalPrediction = prediction("DIS_NONE_XYZ", "Benh khong xac dinh XYZ", "Unknown XYZ", 55.0D);

        long reviewCasesBefore = reviewCaseRepository.count();
        AiDoctorDiagnosisResponse response = aiKnowledgeService.enrichDiagnosis(
                predictResponse(finalPrediction), finalPrediction, "http://img/3.jpg", "",
                "sess-" + System.nanoTime(), 1L, "Anh khong ro nhieu chi tiet.");

        assertThat(response.getStatus()).isEqualTo("UNRECOGNIZED");
        assertThat(response.getNeedsClarification()).isNull();
        assertThat(response.getAiDescription()).contains("Anh khong ro nhieu chi tiet.");
        assertThat(response.getAiDescription()).contains("55%");
        assertThat(response.getAiDescription()).contains("Benh khong xac dinh XYZ");
        assertThat(response.getAiDescription()).contains("Co the do soc moi truong hoac nhiem khuan ngoai da.");
        assertThat(response.getAiDescription()).contains("chưa được kỹ sư xác nhận");
        assertThat(reviewCaseRepository.count()).isEqualTo(reviewCasesBefore + 1);
        AiKnowledgeReviewCase latest = reviewCaseRepository.findTop100ByOrderByCreatedAtDesc().get(0);
        assertThat(latest.getReason()).isEqualTo(AiReviewCaseReason.NO_KNOWLEDGE_MATCH);
    }

    @Test
    void noKnowledgeMatchAtAll_geminiSuggestionFails_gracefullyFallsBackToConfiguredMessage() {
        when(geminiClarifyClient.freeConsult(any(), any(), any()))
                .thenThrow(new RuntimeException("simulated Gemini outage"));

        AiPredictionItem finalPrediction = prediction("DIS_NONE_FAIL", "Benh khong xac dinh FAIL", "Unknown FAIL", 33.0D);

        AiDoctorDiagnosisResponse response = aiKnowledgeService.enrichDiagnosis(
                predictResponse(finalPrediction), finalPrediction, "http://img/3b.jpg", "",
                "sess-" + System.nanoTime(), 1L, null);

        assertThat(response.getStatus()).isEqualTo("UNRECOGNIZED");
        assertThat(response.getAiDescription()).isNotBlank();
    }

    // =========================================================
    // 4) An toan khi Gemini that bai/timeout: geminiImageDescription=null khong duoc crash
    // =========================================================

    @Test
    void nullGeminiImageDescription_isNullSafe_producesCitationOnlyProse_noCrash() {
        AiPredictionItem finalPrediction = prediction("DIS_NONE_ABC", "Benh khong xac dinh ABC", "Unknown ABC", 40.0D);

        AiDoctorDiagnosisResponse response = aiKnowledgeService.enrichDiagnosis(
                predictResponse(finalPrediction), finalPrediction, "http://img/4.jpg", "",
                "sess-" + System.nanoTime(), 1L, null);

        assertThat(response.getAiDescription()).isNotBlank();
        assertThat(response.getAiDescription()).doesNotContain("null");
        assertThat(response.getAiDescription()).contains("40%");
        assertThat(response.getAiDescription()).contains("Benh khong xac dinh ABC");
    }

    // =========================================================
    // 5) Baseline: khoa lai hanh vi nguong confidence/match-score da co san cua enrichDiagnosis
    // (chua tung co test nao bao phu truoc ban doi nay)
    // =========================================================

    @Test
    void matchPass_viaSymptomTextFallback_overridesWeakYoloCode_stillResolvesDisease() throws Exception {
        seedDiseaseWithProtocol("DIS_TXT", "Benh tu van ban", "directkw", 0.3D, 0.65D);
        // Ma benh + ten tu YOLO khong khop gi trong kho tri thuc -> resolvedDisease ban dau null,
        // nhung userSymptoms go tay lai khop truc tiep tu khoa "directkw" cua benh DIS_TXT.
        AiPredictionItem finalPrediction = prediction("UNKNOWN_CODE_123", "Ten khong khop gi", "No match name", 10.0D);

        AiDoctorDiagnosisResponse response = aiKnowledgeService.enrichDiagnosis(
                predictResponse(finalPrediction), finalPrediction, "http://img/5.jpg", "tom bi directkw",
                "sess-" + System.nanoTime(), 1L, null);

        assertThat(response.getStatus()).isEqualTo("DISEASE");
        assertThat(response.getDisease().getCode()).isEqualTo("DIS_TXT");
        assertThat(response.getNeedsClarification()).isNull();
    }

    @Test
    void matchedButBelowThreshold_createsReviewCase_withLowConfidenceReason() throws Exception {
        AiDiseaseKnowledge disease = seedDiseaseWithProtocol("DIS_LC2", "Benh duoi nguong", "khongdungdematch3", 0.6D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_LC2", "Benh duoi nguong", "Below threshold", 15.0D);

        long before = reviewCaseRepository.count();
        aiKnowledgeService.enrichDiagnosis(
                predictResponse(finalPrediction), finalPrediction, "http://img/6.jpg", "",
                "sess-" + System.nanoTime(), 1L, null);

        assertThat(reviewCaseRepository.count()).isEqualTo(before + 1);
        AiKnowledgeReviewCase latest = reviewCaseRepository.findTop100ByOrderByCreatedAtDesc().get(0);
        assertThat(latest.getReason()).isEqualTo(AiReviewCaseReason.LOW_CONFIDENCE);
        assertThat(latest.getMatchedKnowledgeCode()).isEqualTo(disease.getCode());
    }

    @Test
    void directDiseaseCodeMatch_highConfidence_mapsTopPredictionsAndCauses() throws Exception {
        seedDiseaseWithProtocol("DIS_FULL", "Benh day du truong", "khongdungdematch4", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_FULL", "Benh day du truong", "Full fields test", 88.0D);

        AiDoctorDiagnosisResponse response = aiKnowledgeService.enrichDiagnosis(
                predictResponse(finalPrediction), finalPrediction, "http://img/7.jpg", "",
                "sess-" + System.nanoTime(), 1L, null);

        assertThat(response.getTopPredictions()).hasSize(1);
        assertThat(response.getTopPredictions().get(0).getDiseaseCode()).isEqualTo("DIS_FULL");
        assertThat(response.getCauses()).containsExactly("Moi truong bien dong", "Mat do nuoi qua cao");
        assertThat(response.getTreatmentStages()).hasSize(1);
    }
}

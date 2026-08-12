package com.zone.agri.service.aidoctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.client.ai.AiDiagnosisClient;
import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.ai.AiImageNarrativeResult;
import com.zone.agri.dto.ai.AiPredictResponse;
import com.zone.agri.dto.ai.AiPredictionItem;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
import com.zone.agri.repository.AiKnowledgeReviewCaseRepository;
import com.zone.agri.service.ai.AiKnowledgeService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test có chủ đích cho {@code AiDoctorDiagnosisService#diagnose} — đây là lần đầu tiên luồng
 * chẩn đoán qua ảnh (YOLO) có test coverage kể từ khi được xây dựng, tận dụng luôn việc thêm
 * narrative Gemini song song để lấp khoảng trống này.
 *
 * Trọng tâm: (1) response mới có cả aiDescription lẫn các trường cấu trúc cũ; (2) graceful
 * degradation khi Gemini lỗi/timeout — đây là điều bắt buộc phải đúng vì narrative chạy song
 * song với YOLO, không được phép làm hỏng hoặc làm chậm bất thường luồng chẩn đoán chính; (3) MỌI
 * outcome (DISEASE/HEALTHY/UNRECOGNIZED/NON_SHRIMP) đều được lưu vào history khi có userId — để
 * "Sổ khám"/khôi phục chat hôm nay phát lại được, kể cả các case không có bệnh cụ thể.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "security.jwt.secret-key=test-secret-key-for-jwt-util-in-test",
    "security.jwt.issuer=test-issuer",
    "security.jwt.expiry-time-in-seconds=86400",
    "security.jwt.refreshable-duration=86400",
    "mnl.tmp-dir=mnt/",
    "spring.datasource.url=jdbc:h2:mem:agri-doctor-diagnosis-test;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.startup.schema-patches.enabled=false",
    "app.startup.seed-data.enabled=false",
    "spring.data.redis.repositories.enabled=false",
    "ai.base-url=http://localhost:8000",
    "ai.doctor.clarify.max-turns=8",
    "gemini.narrative-join-timeout-seconds=20"
})
@Transactional
class AiDoctorDiagnosisServiceTest {

    @Autowired
    private AiDoctorDiagnosisService aiDoctorDiagnosisService;

    @Autowired
    private AiKnowledgeService aiKnowledgeService;

    @Autowired
    private AiDiseaseKnowledgeRepository diseaseKnowledgeRepository;

    @Autowired
    private AiDoctorDiagnosisHistoryRepository historyRepository;

    @Autowired
    private AiKnowledgeReviewCaseRepository reviewCaseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiDiagnosisClient aiDiagnosisClient;

    @MockitoBean
    private GeminiClarifyClient geminiClarifyClient;

    @BeforeEach
    void resetServiceState() {
        ReflectionTestUtils.setField(aiDoctorDiagnosisService, "narrativeJoinTimeoutSeconds", 20);
        ReflectionTestUtils.invokeMethod(aiKnowledgeService, "evictApprovedSnapshot");
    }

    private void seedDiseaseWithProtocol(String code, String nameVi, String symptomKeyword,
            double matchThreshold, double confidenceThreshold) throws Exception {
        String causesJson = objectMapper.writeValueAsString(List.of("Moi truong bien dong", "Mat do nuoi qua cao"));
        String stagesJson = objectMapper.writeValueAsString(List.of(Map.of(
                "stageTitle", "Giai doan 1: On dinh moi truong",
                "instructions", List.of("Giam soc cho tom", "Tang cuong oxy hoa tan"),
                "productIds", List.of(),
                "extraProductNames", List.of("Vi sinh xu ly day ao"))));
        diseaseKnowledgeRepository.save(AiDiseaseKnowledge.builder()
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

    private void seedDraftDiseaseWithProtocol(String code, String nameVi, String symptomKeyword,
            double matchThreshold, double confidenceThreshold) throws Exception {
        String causesJson = objectMapper.writeValueAsString(List.of("Moi truong bien dong"));
        String stagesJson = objectMapper.writeValueAsString(List.of(Map.of(
                "stageTitle", "Giai doan 1: Xu ly ban dau",
                "instructions", List.of("Theo doi tom"),
                "productIds", List.of(),
                "extraProductNames", List.of())));
        diseaseKnowledgeRepository.save(AiDiseaseKnowledge.builder()
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
                .status(AiKnowledgeStatus.DRAFT)
                .build());
    }

    private MockMultipartFile fakeImage() {
        return new MockMultipartFile("image", "shrimp.jpg", "image/jpeg", "fake-image-bytes".getBytes());
    }

    private AiPredictionItem prediction(String diseaseCode, String nameVi, String nameEn, double confidencePercent) {
        AiPredictionItem item = new AiPredictionItem();
        item.setDiseaseCode(diseaseCode);
        item.setVietnameseName(nameVi);
        item.setEnglishName(nameEn);
        item.setConfidencePercent(confidencePercent);
        return item;
    }

    private AiPredictResponse predictResponse(String status, AiPredictionItem finalPrediction) {
        AiPredictResponse response = new AiPredictResponse();
        response.setSuccess(true);
        response.setStatus(status);
        response.setFinalPrediction(finalPrediction);
        response.setTopPredictions(finalPrediction != null ? List.of(finalPrediction) : List.of());
        return response;
    }

    /** Ket qua Gemini describeImage() gia lap cho anh THAT LA TOM (isShrimp=true) — truong hop mac dinh
     * cua da so test hien co, chi quan tam noi dung mo ta chu khong quan tam co la tom hay khong. */
    private AiImageNarrativeResult narrative(String description) {
        return AiImageNarrativeResult.builder().isShrimp(true).description(description).build();
    }

    // =========================================================
    // 1) Response DISEASE co ca aiDescription lan cac truong cau truc cu
    // =========================================================

    @Test
    void diseaseStatus_responseHasAiDescriptionAndStructuredFields() throws Exception {
        seedDiseaseWithProtocol("DIS_E2E", "Benh e2e test", "e2ekeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_E2E", "Benh e2e test", "E2E test", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn(narrative("Gemini mo ta anh e2e."));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-1");

        assertThat(response.getStatus()).isEqualTo("DISEASE");
        assertThat(response.getAiDescription()).contains("Gemini mo ta anh e2e.");
        assertThat(response.getAiDescription()).contains("90%");
        assertThat(response.getAiDescription()).contains("Giai doan 1: On dinh moi truong");
        assertThat(response.getDisease().getCode()).isEqualTo("DIS_E2E");
    }

    // =========================================================
    // 2) Gemini narrative throw -> van tra ve day du response duong dan YOLO (graceful degradation)
    // =========================================================

    @Test
    void geminiNarrativeThrows_stillReturnsFullYoloDrivenResponse_gracefulDegradation() throws Exception {
        seedDiseaseWithProtocol("DIS_GRACE", "Benh graceful test", "gracekeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_GRACE", "Benh graceful test", "Graceful test", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any()))
                .thenThrow(new RuntimeException("simulated Gemini outage"));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-2");

        assertThat(response.getStatus()).isEqualTo("DISEASE");
        assertThat(response.getDisease().getCode()).isEqualTo("DIS_GRACE");
        assertThat(response.getTreatmentStages()).isNotEmpty();
        // Khong co mo ta Gemini nhung van con cau trich dan + phac do -> aiDescription khong rong.
        assertThat(response.getAiDescription()).isNotBlank();
        assertThat(response.getAiDescription()).contains("Giai doan 1: On dinh moi truong");
    }

    // =========================================================
    // 3) Gemini narrative vuot join timeout -> van tra ve dung han, suy giam an toan
    // =========================================================

    @Test
    void geminiNarrativeExceedsJoinTimeout_stillReturnsPromptly_degradesGracefully() throws Exception {
        ReflectionTestUtils.setField(aiDoctorDiagnosisService, "narrativeJoinTimeoutSeconds", 1);
        seedDiseaseWithProtocol("DIS_SLOW", "Benh slow test", "slowkeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_SLOW", "Benh slow test", "Slow test", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(3000);
            return narrative("Cau tra loi qua cham, khong duoc dung.");
        });

        long start = System.currentTimeMillis();
        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-3");
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(elapsedMs).isLessThan(2500L);
        assertThat(response.getStatus()).isEqualTo("DISEASE");
        assertThat(response.getAiDescription()).doesNotContain("qua cham");
    }

    // =========================================================
    // 4) BLURRY/NON_SHRIMP/HEALTHY khong bi anh huong boi phan narrative moi them
    // =========================================================

    @Test
    void blurryStatus_throwsBadRequest_unaffectedByNarrativeAddition() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("BLURRY", null));

        assertThatThrownBy(() -> aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-4"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void nonShrimpStatus_returnsGeminiNarrative_insteadOfGenericBadRequest() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("NON_SHRIMP", null));
        when(geminiClarifyClient.describeImage(any(), any(), any()))
                .thenReturn(AiImageNarrativeResult.builder().isShrimp(false)
                        .description("Day la anh mot chiec la, khong phai tom.").build());

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-5");

        assertThat(response.getStatus()).isEqualTo("UNRECOGNIZED");
        assertThat(response.getAiDescription()).contains("Day la anh mot chiec la, khong phai tom.");
        // Khong duoc goi Gemini lan 2 (freeConsult) — chi 1 doan van duy nhat, tranh 2 doan Gemini
        // doc lap ghep vao nhau moi doan lai tu chao rieng (bi thua/lap y).
        verify(geminiClarifyClient, never()).freeConsult(any(), any(), any());

        // Phai luu history de "So kham"/khoi phuc chat hom nay phat lai duoc bong bong nay.
        AiDoctorDiagnosisHistory saved = historyRepository.findByIdAndUserId(Long.valueOf(response.getDiagnosisId()), 1L)
                .orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("UNRECOGNIZED");
        assertThat(saved.getAiDescription()).contains("Day la anh mot chiec la, khong phai tom.");
        assertThat(saved.getFinalDiseaseCode()).isNull();
    }

    @Test
    void healthyStatus_returnsHealthyResponse_noAiDescription() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("HEALTHY", null));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-6");

        assertThat(response.getStatus()).isEqualTo("HEALTHY");
        assertThat(response.getAiDescription()).isNull();

        AiDoctorDiagnosisHistory saved = historyRepository.findByIdAndUserId(Long.valueOf(response.getDiagnosisId()), 1L)
                .orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("HEALTHY");
        assertThat(saved.getFinalDiseaseCode()).isNull();
    }

    // =========================================================
    // 4c. YOLO nham anh KHONG PHAI TOM thanh HEALTHY (vd cho/meo/do vat) — Gemini xac nhan
    // isShrimp=false phai chan lai, khong duoc tra HEALTHY sai cho nguoi dung. Day la regression
    // test cho dung bug thuc te: anh 1 con cho duoc YOLO gan nham vao lop Healthy.
    // =========================================================

    @Test
    void healthyStatus_geminiConfirmsNotShrimp_overriddenToUnrecognized_insteadOfFalseHealthy() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("HEALTHY", null));
        when(geminiClarifyClient.describeImage(any(), any(), any()))
                .thenReturn(AiImageNarrativeResult.builder().isShrimp(false)
                        .description("Day la anh mot chu cho, khong phai tom.").build());

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-6b");

        assertThat(response.getStatus()).isEqualTo("UNRECOGNIZED");
        assertThat(response.getAiDescription()).contains("Day la anh mot chu cho, khong phai tom.");
    }

    // Gemini fail/timeout khi YOLO tra HEALTHY -> fail-open, GIU NGUYEN HEALTHY (khong regress hanh
    // vi cu chi vi luoi an toan khong hoat dong duoc luc do).
    @Test
    void healthyStatus_geminiFails_failsOpen_stillReturnsHealthy() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("HEALTHY", null));
        when(geminiClarifyClient.describeImage(any(), any(), any()))
                .thenThrow(new RuntimeException("simulated Gemini outage"));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-6c");

        assertThat(response.getStatus()).isEqualTo("HEALTHY");
    }

    // =========================================================
    // 4b. finalPrediction null (YOLO khong nhan ra benh cu the tu anh) -> goi Gemini goi y tu do
    // (nhu free-consult) + luon kem lien he ky su, thay vi throw BadRequestException nhu truoc.
    // =========================================================

    @Test
    void finalPredictionNull_callsGeminiFreeSuggestion_returnsUnrecognizedStatus_insteadOfThrowing() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", null));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn(narrative("Anh mo, kho nhan dinh ro."));
        when(geminiClarifyClient.freeConsult(any(), any(), any()))
                .thenReturn("Co the do moi truong bien dong, ban theo doi them vai ngay nhe.");

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-8");

        assertThat(response.getStatus()).isEqualTo("UNRECOGNIZED");
        assertThat(response.getAiDescription()).contains("Co the do moi truong bien dong, ban theo doi them vai ngay nhe.");
        assertThat(response.getAiDescription()).contains("chưa được kỹ sư xác nhận");

        AiDoctorDiagnosisHistory saved = historyRepository.findByIdAndUserId(Long.valueOf(response.getDiagnosisId()), 1L)
                .orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("UNRECOGNIZED");
        assertThat(saved.getAiDescription()).contains("Co the do moi truong bien dong, ban theo doi them vai ngay nhe.");
    }

    @Test
    void finalPredictionNull_geminiSuggestionFails_stillReturnsGracefully() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", null));
        when(geminiClarifyClient.freeConsult(any(), any(), any()))
                .thenThrow(new RuntimeException("simulated Gemini outage"));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-9");

        assertThat(response.getStatus()).isEqualTo("UNRECOGNIZED");
        assertThat(response.getAiDescription()).isNotBlank();
    }

    // =========================================================
    // 5) History luu dung cot cho DISEASE, ke ca status/aiDescription moi them (truoc day
    // aiDescription co chu dich KHONG duoc luu — EPHEMERAL; nay doi lai de phuc vu phat lai o
    // "So kham"/khoi phuc chat hom nay, ap dung dong nhat cho moi status chu khong rieng gi
    // HEALTHY/UNRECOGNIZED).
    // =========================================================

    @Test
    void historyRow_persistsStatusAndAiDescription_forDiseaseOutcome() throws Exception {
        seedDiseaseWithProtocol("DIS_HIST", "Benh history test", "histkeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_HIST", "Benh history test", "History test", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn(narrative("Mo ta luu history."));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 42L, "sess-7");

        Long historyId = Long.valueOf(response.getDiagnosisId());
        AiDoctorDiagnosisHistory saved = historyRepository.findByIdAndUserId(historyId, 42L).orElseThrow();
        assertThat(saved.getFinalDiseaseCode()).isEqualTo("DIS_HIST");
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getStatus()).isEqualTo("DISEASE");
        assertThat(saved.getAiDescription()).contains("Mo ta luu history.");
    }

    @Test
    void diagnose_guestUser_doesNotSaveHistory() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("HEALTHY", null));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", null, "sess-guest");

        assertThat(response.getStatus()).isEqualTo("HEALTHY");
        assertThat(response.getDiagnosisId()).startsWith("healthy_");
    }

    // =========================================================
    // Chat thu nghiem — diagnoseForTest: xem truoc phac do chua duyet, khong tao review case that
    // =========================================================

    @Test
    void diagnoseForTest_withoutPreview_draftDiseaseIsUnrecognized() throws Exception {
        seedDraftDiseaseWithProtocol("DIS_DRAFT_IMG", "Benh nhap chua duyet qua anh", "draftimgkeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_DRAFT_IMG", "Benh nhap chua duyet qua anh", "Draft img", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn(narrative("Mo ta anh."));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-draft-1");

        assertThat(response.getStatus()).isEqualTo("UNRECOGNIZED");
    }

    @Test
    void diagnoseForTest_withPreviewDiseaseCode_matchesDraftDisease() throws Exception {
        seedDraftDiseaseWithProtocol("DIS_DRAFT_PREVIEW", "Benh nhap xem truoc qua anh", "previewimgkeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_DRAFT_PREVIEW", "Benh nhap xem truoc qua anh", "Preview img", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn(narrative("Mo ta anh preview."));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnoseForTest(fakeImage(), "", "DIS_DRAFT_PREVIEW");

        assertThat(response.getStatus()).isEqualTo("DISEASE");
        assertThat(response.getDisease().getCode()).isEqualTo("DIS_DRAFT_PREVIEW");
    }

    @Test
    void diagnoseForTest_guestAlways_neverSavesHistory() throws Exception {
        seedDraftDiseaseWithProtocol("DIS_DRAFT_NOHIST", "Benh nhap khong luu history", "nohistkeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_DRAFT_NOHIST", "Benh nhap khong luu history", "No hist", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn(narrative("Mo ta anh."));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnoseForTest(fakeImage(), "", "DIS_DRAFT_NOHIST");

        assertThat(response.getStatus()).isEqualTo("DISEASE");
        assertThat(historyRepository.findAll()).isEmpty();
    }

    @Test
    void diagnoseForTest_lowConfidenceMatch_doesNotCreateRealReviewCase() throws Exception {
        seedDraftDiseaseWithProtocol("DIS_DRAFT_LOWCONF", "Benh nhap do tin cay thap", "lowconfkeyword", 0.99D, 0.99D);
        AiPredictionItem finalPrediction = prediction("DIS_DRAFT_LOWCONF", "Benh nhap do tin cay thap", "Low conf", 10.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn(narrative("Mo ta anh."));
        long reviewCaseCountBefore = reviewCaseRepository.count();

        aiDoctorDiagnosisService.diagnoseForTest(fakeImage(), "", "DIS_DRAFT_LOWCONF");

        assertThat(reviewCaseRepository.count()).isEqualTo(reviewCaseCountBefore);
    }
}

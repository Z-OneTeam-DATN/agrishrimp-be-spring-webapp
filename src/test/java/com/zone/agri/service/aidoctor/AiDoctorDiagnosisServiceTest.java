package com.zone.agri.service.aidoctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.client.ai.AiDiagnosisClient;
import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.ai.AiPredictResponse;
import com.zone.agri.dto.ai.AiPredictionItem;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
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
 * song với YOLO, không được phép làm hỏng hoặc làm chậm bất thường luồng chẩn đoán chính; (3) các
 * trạng thái đặc biệt (BLURRY/NON_SHRIMP/HEALTHY) không bị ảnh hưởng; (4) history vẫn lưu đúng các
 * cột như trước, không có cột mới nào cho narrative (thiết kế có chủ đích: EPHEMERAL).
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

    // =========================================================
    // 1) Response DISEASE co ca aiDescription lan cac truong cau truc cu
    // =========================================================

    @Test
    void diseaseStatus_responseHasAiDescriptionAndStructuredFields() throws Exception {
        seedDiseaseWithProtocol("DIS_E2E", "Benh e2e test", "e2ekeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_E2E", "Benh e2e test", "E2E test", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn("Gemini mo ta anh e2e.");

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
            return "Cau tra loi qua cham, khong duoc dung.";
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
    void nonShrimpStatus_throwsBadRequest_unaffectedByNarrativeAddition() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("NON_SHRIMP", null));

        assertThatThrownBy(() -> aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-5"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void healthyStatus_returnsHealthyResponse_noAiDescription() {
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("HEALTHY", null));

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 1L, "sess-6");

        assertThat(response.getStatus()).isEqualTo("HEALTHY");
        assertThat(response.getAiDescription()).isNull();
    }

    // =========================================================
    // 5) History van luu dung cac cot nhu truoc - khong co cot moi nao cho narrative (EPHEMERAL)
    // =========================================================

    @Test
    void historyRow_hasNoAiDescriptionColumn_ephemeralFieldNeverPersisted() throws Exception {
        assertThatThrownBy(() -> AiDoctorDiagnosisHistory.class.getDeclaredField("aiDescription"))
                .isInstanceOf(NoSuchFieldException.class);

        seedDiseaseWithProtocol("DIS_HIST", "Benh history test", "histkeyword", 0.4D, 0.65D);
        AiPredictionItem finalPrediction = prediction("DIS_HIST", "Benh history test", "History test", 90.0D);
        when(aiDiagnosisClient.predict(any())).thenReturn(predictResponse("DISEASE", finalPrediction));
        when(geminiClarifyClient.describeImage(any(), any(), any())).thenReturn("Mo ta luu history.");

        AiDoctorDiagnosisResponse response = aiDoctorDiagnosisService.diagnose(fakeImage(), "", 42L, "sess-7");

        Long historyId = Long.valueOf(response.getDiagnosisId());
        AiDoctorDiagnosisHistory saved = historyRepository.findByIdAndUserId(historyId, 42L).orElseThrow();
        assertThat(saved.getFinalDiseaseCode()).isEqualTo("DIS_HIST");
        assertThat(saved.getUserId()).isEqualTo(42L);
    }
}

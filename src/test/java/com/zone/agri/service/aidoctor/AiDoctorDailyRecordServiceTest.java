package com.zone.agri.service.aidoctor;

import static org.assertj.core.api.Assertions.assertThat;

import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.response.ai.AiDoctorDailyRecordDetailResponse;
import com.zone.agri.dto.response.ai.AiDoctorDailyRecordListResponse;
import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.AiKnowledgeChatLog;
import com.zone.agri.entity.enums.AiKnowledgeMatchType;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import com.zone.agri.repository.AiDiseaseKnowledgeRepository;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
import com.zone.agri.repository.AiKnowledgeChatLogRepository;
import com.zone.agri.service.ai.AiKnowledgeService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Test cho tính năng "Sổ khám" — {@code AiDoctorDailyRecordService} gộp on-demand từ
 * {@code AiKnowledgeChatLog} (chat chữ) + {@code AiDoctorDiagnosisHistory} (chẩn đoán qua ảnh),
 * không lưu entity riêng nào. Trọng tâm: (1) ngày chỉ có ảnh, không chat, vẫn phải xuất hiện —
 * đây là phát hiện quan trọng nhất khi thiết kế tính năng này; (2) lọc đúng sourceChannel; (3)
 * group đúng biên ngày; (4) chi tiết ngày lấy đúng symptoms/diseases/diagnoses của đúng ngày đó.
 *
 * createdAt kế thừa từ BaseEntity có {@code @Column(updatable = false)} — set qua setter/builder
 * rồi save() lại sẽ bị Hibernate BỎ QUA cột này trong UPDATE. Dùng native query trực tiếp qua
 * EntityManager để ép giá trị createdAt test mong muốn, rồi clear() persistence context để lần
 * đọc sau qua repository lấy đúng giá trị mới từ DB, không phải bản cũ còn cache trong session.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "security.jwt.secret-key=test-secret-key-for-jwt-util-in-test",
    "security.jwt.issuer=test-issuer",
    "security.jwt.expiry-time-in-seconds=86400",
    "security.jwt.refreshable-duration=86400",
    "mnl.tmp-dir=mnt/",
    "spring.datasource.url=jdbc:h2:mem:agri-daily-record-test;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
class AiDoctorDailyRecordServiceTest {

    @Autowired
    private AiDoctorDailyRecordService dailyRecordService;

    @Autowired
    private AiKnowledgeChatLogRepository chatLogRepository;

    @Autowired
    private AiDoctorDiagnosisHistoryRepository diagnosisHistoryRepository;

    @Autowired
    private AiDiseaseKnowledgeRepository diseaseKnowledgeRepository;

    @Autowired
    private AiKnowledgeService aiKnowledgeService;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private GeminiClarifyClient geminiClarifyClient;

    @BeforeEach
    void resetServiceState() {
        ReflectionTestUtils.invokeMethod(aiKnowledgeService, "evictApprovedSnapshot");
    }

    private void forceCreatedAt(String tableName, Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE " + tableName + " SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private void seedChatLog(Long userId, String sourceChannel, String questionText, boolean matched,
            AiKnowledgeMatchType matchedType, String matchedKnowledgeCode, LocalDateTime createdAt) {
        AiKnowledgeChatLog saved = chatLogRepository.save(AiKnowledgeChatLog.builder()
                .userId(userId)
                .sourceChannel(sourceChannel)
                .questionText(questionText)
                .answerText("dummy answer")
                .matched(matched)
                .matchedType(matchedType)
                .matchedKnowledgeCode(matchedKnowledgeCode)
                .build());
        forceCreatedAt("ai_knowledge_chat_logs", saved.getId(), createdAt);
    }

    private void seedDiagnosisHistory(Long userId, String diseaseCode, String nameVi, LocalDateTime createdAt) {
        AiDoctorDiagnosisHistory saved = diagnosisHistoryRepository.save(AiDoctorDiagnosisHistory.builder()
                .userId(userId)
                .imageUrl("http://img/test.jpg")
                .finalDiseaseCode(diseaseCode)
                .finalDiseaseNameVi(nameVi)
                .finalConfidencePercent(90.0)
                .needsClarification(false)
                .build());
        forceCreatedAt("miniapp_diagnosis_history", saved.getId(), createdAt);
    }

    private void seedApprovedDisease(String code, String nameVi) {
        diseaseKnowledgeRepository.save(AiDiseaseKnowledge.builder()
                .code(code)
                .nameVi(nameVi)
                .symptomKeywordsRaw("khongdungdematch")
                .signsSummary("Dau hieu test")
                .confidenceThreshold(0.65D)
                .matchThreshold(0.4D)
                .enabled(true)
                .priority(0)
                .canonical(false)
                .status(AiKnowledgeStatus.APPROVED)
                .build());
        ReflectionTestUtils.invokeMethod(aiKnowledgeService, "evictApprovedSnapshot");
    }

    @Test
    void imageOnlyDay_noChatAtAll_stillAppearsInDatesList() {
        Long userId = 1001L;
        seedDiagnosisHistory(userId, "DIS_X", "Benh X", LocalDateTime.of(2026, 6, 15, 10, 0));

        AiDoctorDailyRecordListResponse response = dailyRecordService.getDailyRecordDates(userId);

        assertThat(response.getDates()).contains("2026-06-15");
    }

    @Test
    void sourceChannelOtherThanPrivate_isExcludedFromDatesList() {
        Long userId = 1002L;
        seedChatLog(userId, "AI_DOCTOR_PUBLIC", "tom bi benh gi", false, null, null,
                LocalDateTime.of(2026, 6, 16, 10, 0));

        AiDoctorDailyRecordListResponse response = dailyRecordService.getDailyRecordDates(userId);

        assertThat(response.getDates()).doesNotContain("2026-06-16");
    }

    @Test
    void dayBoundary_endOfDayAndStartOfNextDay_areNotMixed() {
        Long userId = 1003L;
        seedChatLog(userId, "AI_DOCTOR_PRIVATE", "cau hoi ngay 1", false, null, null,
                LocalDateTime.of(2026, 6, 20, 23, 59, 59));
        seedChatLog(userId, "AI_DOCTOR_PRIVATE", "cau hoi ngay 2", false, null, null,
                LocalDateTime.of(2026, 6, 21, 0, 0, 0));

        AiDoctorDailyRecordDetailResponse day1 = dailyRecordService.getDailyRecordDetail(userId, LocalDate.of(2026, 6, 20));
        AiDoctorDailyRecordDetailResponse day2 = dailyRecordService.getDailyRecordDetail(userId, LocalDate.of(2026, 6, 21));

        assertThat(day1.getSymptomsDescribed()).containsExactly("cau hoi ngay 1");
        assertThat(day2.getSymptomsDescribed()).containsExactly("cau hoi ngay 2");
    }

    @Test
    void detail_returnsSymptomsAndDiseasesAndDiagnoses_scopedToExactDay() throws Exception {
        seedApprovedDisease("DIS_MATCHED", "Benh da khop");
        Long userId = 1004L;
        LocalDateTime day = LocalDateTime.of(2026, 6, 22, 9, 0);
        LocalDateTime otherDay = LocalDateTime.of(2026, 6, 23, 9, 0);

        seedChatLog(userId, "AI_DOCTOR_PRIVATE", "tom boi lo do", true,
                AiKnowledgeMatchType.DISEASE_KNOWLEDGE, "DIS_MATCHED", day);
        seedChatLog(userId, "AI_DOCTOR_PRIVATE", "cau hoi ngay khac", false, null, null, otherDay);
        seedDiagnosisHistory(userId, "DIS_IMG", "Benh anh", day);
        seedDiagnosisHistory(userId, "DIS_IMG_OTHER_DAY", "Benh ngay khac", otherDay);

        AiDoctorDailyRecordDetailResponse response = dailyRecordService.getDailyRecordDetail(userId, LocalDate.of(2026, 6, 22));

        assertThat(response.getSymptomsDescribed()).containsExactly("tom boi lo do");
        assertThat(response.getDiseasesDiscussed()).hasSize(1);
        assertThat(response.getDiseasesDiscussed().get(0).getNameVi()).isEqualTo("Benh da khop");
        assertThat(response.getDiagnoses()).hasSize(1);
        assertThat(response.getDiagnoses().get(0).getDisease().getCode()).isEqualTo("DIS_IMG");
    }

    @Test
    void detail_unmatchedChatMessages_areNotIncludedInDiseasesDiscussed() {
        Long userId = 1005L;
        LocalDateTime day = LocalDateTime.of(2026, 6, 24, 9, 0);
        seedChatLog(userId, "AI_DOCTOR_PRIVATE", "tom bi gi vay", false, AiKnowledgeMatchType.AMBIGUOUS, null, day);

        AiDoctorDailyRecordDetailResponse response = dailyRecordService.getDailyRecordDetail(userId, LocalDate.of(2026, 6, 24));

        assertThat(response.getSymptomsDescribed()).containsExactly("tom bi gi vay");
        assertThat(response.getDiseasesDiscussed()).isEmpty();
    }

    @Test
    void detail_dayWithNoData_returnsEmptyListsGracefully() {
        AiDoctorDailyRecordDetailResponse response = dailyRecordService.getDailyRecordDetail(9999L, LocalDate.of(2026, 1, 1));

        assertThat(response.getSymptomsDescribed()).isEmpty();
        assertThat(response.getDiseasesDiscussed()).isEmpty();
        assertThat(response.getDiagnoses()).isEmpty();
    }
}

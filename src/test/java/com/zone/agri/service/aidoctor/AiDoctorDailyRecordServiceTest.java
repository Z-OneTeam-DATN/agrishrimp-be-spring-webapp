package com.zone.agri.service.aidoctor;

import static org.assertj.core.api.Assertions.assertThat;

import com.zone.agri.client.ai.GeminiClarifyClient;
import com.zone.agri.dto.response.ai.AiDoctorConversationTurnResponse;
import com.zone.agri.dto.response.ai.AiDoctorDailyRecordListResponse;
import com.zone.agri.entity.AiDoctorDiagnosisHistory;
import com.zone.agri.entity.AiKnowledgeChatLog;
import com.zone.agri.entity.enums.AiKnowledgeMatchType;
import com.zone.agri.repository.AiDoctorDiagnosisHistoryRepository;
import com.zone.agri.repository.AiKnowledgeChatLogRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test cho tính năng "Sổ khám" — {@code AiDoctorDailyRecordService} gộp on-demand từ
 * {@code AiKnowledgeChatLog} (chat chữ) + {@code AiDoctorDiagnosisHistory} (chẩn đoán qua ảnh),
 * không lưu entity riêng nào. Trọng tâm: (1) ngày chỉ có ảnh, không chat, vẫn phải xuất hiện —
 * đây là phát hiện quan trọng nhất khi thiết kế tính năng này; (2) lọc đúng sourceChannel; (3)
 * group/phát lại đúng biên ngày và đúng thứ tự thời gian, không lẫn giữa các ngày.
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
    private EntityManager entityManager;

    @MockitoBean
    private GeminiClarifyClient geminiClarifyClient;

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

        List<AiDoctorConversationTurnResponse> day1 = dailyRecordService.getConversation(userId, LocalDate.of(2026, 6, 20));
        List<AiDoctorConversationTurnResponse> day2 = dailyRecordService.getConversation(userId, LocalDate.of(2026, 6, 21));

        assertThat(day1).hasSize(1);
        assertThat(day1.get(0).getQuestionText()).isEqualTo("cau hoi ngay 1");
        assertThat(day2).hasSize(1);
        assertThat(day2.get(0).getQuestionText()).isEqualTo("cau hoi ngay 2");
    }

    @Test
    void conversation_mergesChatAndDiagnosisInChronologicalOrder() {
        Long userId = 2001L;
        LocalDate date = LocalDate.of(2026, 6, 22);
        LocalDateTime at9am = date.atTime(9, 0);
        LocalDateTime at930am = date.atTime(9, 30);
        LocalDateTime at10am = date.atTime(10, 0);

        // Co tinh seed KHONG theo thu tu (chat 2 truoc, roi diagnosis, roi chat 1) de xac nhan
        // service tu sort lai dung theo createdAt chu khong dua vao thu tu insert.
        seedChatLog(userId, "AI_DOCTOR_PRIVATE", "cau hoi luc 10h", true, null, null, at10am);
        seedDiagnosisHistory(userId, "DIS_MID", "Benh giua", at930am);
        seedChatLog(userId, "AI_DOCTOR_PRIVATE", "cau hoi luc 9h", true, null, null, at9am);

        List<AiDoctorConversationTurnResponse> turns = dailyRecordService.getConversation(userId, date);

        assertThat(turns).hasSize(3);
        assertThat(turns.get(0).getType()).isEqualTo("CHAT");
        assertThat(turns.get(0).getQuestionText()).isEqualTo("cau hoi luc 9h");
        assertThat(turns.get(1).getType()).isEqualTo("DIAGNOSIS");
        assertThat(turns.get(1).getDisease().getCode()).isEqualTo("DIS_MID");
        assertThat(turns.get(2).getType()).isEqualTo("CHAT");
        assertThat(turns.get(2).getQuestionText()).isEqualTo("cau hoi luc 10h");
    }

    @Test
    void conversation_excludesOtherDays() {
        Long userId = 2002L;
        seedChatLog(userId, "AI_DOCTOR_PRIVATE", "cau hoi hom truoc", true, null, null,
                LocalDateTime.of(2026, 6, 23, 9, 0));
        seedDiagnosisHistory(userId, "DIS_OTHER_DAY", "Benh hom truoc", LocalDateTime.of(2026, 6, 23, 9, 30));

        List<AiDoctorConversationTurnResponse> turns = dailyRecordService.getConversation(userId, LocalDate.of(2026, 6, 24));

        assertThat(turns).isEmpty();
    }

    @Test
    void conversation_dayWithNoData_returnsEmptyList() {
        List<AiDoctorConversationTurnResponse> turns = dailyRecordService.getConversation(9999L, LocalDate.of(2026, 1, 1));

        assertThat(turns).isEmpty();
    }
}

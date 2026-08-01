package com.zone.agri.entity;

import com.zone.agri.entity.enums.AiClarifySessionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * Phiên hỏi-đáp LLM (Gemini) để thu hẹp bệnh khi chat gõ chữ tự do khớp mơ hồ (nhiều bệnh cùng
 * qua ngưỡng) hoặc gần đạt ngưỡng 1 bệnh — tái dùng đúng GeminiClarifyClient/schema-guardrail của
 * luồng ảnh (AiDoctorClarifySession), chỉ khác nơi khoá phiên: theo sessionId của chat thay vì
 * diagnosisId của ảnh. Xem AiKnowledgeService#answerChat để biết luồng xử lý đầy đủ.
 */
@Entity
@Table(name = "ai_chat_clarify_sessions",
        indexes = {
                @Index(name = "idx_chat_clarify_session_id", columnList = "session_id"),
                @Index(name = "idx_chat_clarify_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiChatClarifySession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "session_id", nullable = false, length = 100)
    String sessionId;

    @Column(name = "user_id")
    Long userId;

    @Column(name = "source_channel", length = 50)
    String sourceChannel;

    /** JSON array các mã bệnh candidate đã chốt lúc bắt đầu phiên — không đổi trong suốt phiên. */
    @Column(name = "candidate_disease_codes_json", columnDefinition = "TEXT")
    String candidateDiseaseCodesJson;

    /** JSON array {role: ASSISTANT|FARMER, text} — toàn bộ lượt hỏi-đáp, gửi lại cho Gemini mỗi lượt. */
    @Column(name = "conversation_json", columnDefinition = "LONGTEXT")
    String conversationJson;

    /** Số câu hỏi AI đã hỏi — so với ai.doctor.clarify.max-turns để chặn lặp vô hạn. */
    @Builder.Default
    @Column(name = "turn_count", nullable = false)
    Integer turnCount = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    AiClarifySessionStatus status = AiClarifySessionStatus.ACTIVE;

    @Column(name = "decided_disease_code", length = 50)
    String decidedDiseaseCode;

    @Column(name = "review_case_id")
    Long reviewCaseId;
}

package com.zone.agri.entity;

import com.zone.agri.entity.enums.AiClarifySessionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

/**
 * Một phiên hỏi-đáp LLM (Gemini) để thu hẹp bệnh khi ảnh AI Doctor có độ tin cậy thấp.
 *
 * Khoá theo diagnosisId (không phải sessionId) vì diagnosisId luôn tồn tại cho MỌI lần
 * diagnose() — kể cả khách vãng lai (guest, không có history row DB) — trong khi sessionId
 * chỉ được FE gửi cho luồng public/guest. Xem AiDoctorClarifyService để biết luồng xử lý đầy đủ.
 */
@Entity
@Table(name = "ai_doctor_clarify_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_clarify_diagnosis_id", columnNames = "diagnosis_id"),
        indexes = {
                @Index(name = "idx_clarify_diagnosis_id", columnList = "diagnosis_id"),
                @Index(name = "idx_clarify_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiDoctorClarifySession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "diagnosis_id", nullable = false, length = 64)
    String diagnosisId;

    @Column(name = "user_id")
    Long userId;

    /** Id numeric của AiDoctorDiagnosisHistory — null với khách vãng lai (không có history row). */
    @Column(name = "diagnosis_history_id")
    Long diagnosisHistoryId;

    @Column(name = "source_channel", length = 50)
    String sourceChannel;

    @Column(name = "image_url", columnDefinition = "TEXT")
    String imageUrl;

    @Column(name = "initial_symptoms", columnDefinition = "TEXT")
    String initialSymptoms;

    /** Mã bệnh YOLO đoán ban đầu (top-1) — dùng làm aiSuggestedDiseaseCode nếu phải escalate. */
    @Column(name = "ai_suggested_disease_code", length = 50)
    String aiSuggestedDiseaseCode;

    /** JSON array các mã bệnh candidate đã chốt lúc bắt đầu phiên — không đổi trong suốt phiên. */
    @Column(name = "candidate_disease_codes_json", columnDefinition = "TEXT")
    String candidateDiseaseCodesJson;

    /** JSON array {turnIndex, role: ASSISTANT|FARMER, text} — toàn bộ lượt hỏi-đáp. */
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

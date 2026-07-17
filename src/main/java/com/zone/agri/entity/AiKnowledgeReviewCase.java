package com.zone.agri.entity;

import com.zone.agri.entity.enums.AiReviewCaseReason;
import com.zone.agri.entity.enums.AiReviewCaseStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_knowledge_review_cases",
        indexes = {
                @Index(name = "idx_ai_review_case_status", columnList = "status"),
                @Index(name = "idx_ai_review_case_reason", columnList = "reason")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiKnowledgeReviewCase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id")
    Long userId;

    @Column(name = "session_id", length = 120)
    String sessionId;

    @Column(name = "source_channel", length = 50)
    String sourceChannel;

    @Column(name = "question_text", columnDefinition = "LONGTEXT")
    String questionText;

    @Column(name = "user_symptoms", columnDefinition = "LONGTEXT")
    String userSymptoms;

    @Column(name = "image_url", columnDefinition = "TEXT")
    String imageUrl;

    @Column(name = "ai_suggested_disease_code", length = 120)
    String aiSuggestedDiseaseCode;

    @Column(name = "matched_knowledge_code", length = 120)
    String matchedKnowledgeCode;

    @Column(name = "match_score")
    Double matchScore;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    AiReviewCaseReason reason = AiReviewCaseReason.NO_KNOWLEDGE_MATCH;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    AiReviewCaseStatus status = AiReviewCaseStatus.NEW;

    @Column(name = "resolution_notes", columnDefinition = "LONGTEXT")
    String resolutionNotes;
}

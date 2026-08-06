package com.zone.agri.entity;

import com.zone.agri.entity.enums.AiKnowledgeMatchType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_knowledge_chat_logs",
        indexes = {
                @Index(name = "idx_ai_chat_logs_matched", columnList = "matched"),
                @Index(name = "idx_ai_chat_logs_matched_type", columnList = "matched_type")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiKnowledgeChatLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id")
    Long userId;

    @Column(name = "session_id", length = 120)
    String sessionId;

    @Column(name = "source_channel", length = 50)
    String sourceChannel;

    @Column(name = "question_text", columnDefinition = "LONGTEXT", nullable = false)
    String questionText;

    @Column(name = "answer_text", columnDefinition = "LONGTEXT")
    String answerText;

    @Builder.Default
    @Column(nullable = false)
    Boolean matched = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "matched_type", length = 30)
    AiKnowledgeMatchType matchedType;

    @Column(name = "matched_knowledge_code", length = 120)
    String matchedKnowledgeCode;

    @Column(name = "match_score")
    Double matchScore;
}

package com.zone.agri.entity;

import com.zone.agri.entity.enums.AiKnowledgeStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_keyword_answer_sets",
        indexes = {
                @Index(name = "idx_ai_keyword_answer_sets_code", columnList = "code"),
                @Index(name = "idx_ai_keyword_answer_sets_status", columnList = "status"),
                @Index(name = "idx_ai_keyword_answer_sets_enabled", columnList = "enabled")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiKeywordAnswerSet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true, length = 120)
    String code;

    @Column(nullable = false, length = 180)
    String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    AiKnowledgeCategory category;

    @Column(name = "keywords_raw", columnDefinition = "TEXT", nullable = false)
    String keywordsRaw;

    @Column(name = "answer_html", columnDefinition = "LONGTEXT", nullable = false)
    String answerHtml;

    @Builder.Default
    @Column(nullable = false)
    Boolean enabled = true;

    @Builder.Default
    @Column(name = "match_threshold", nullable = false)
    Double matchThreshold = 0.35d;

    @Builder.Default
    @Column(nullable = false)
    Integer priority = 0;

    @Builder.Default
    @Column(name = "is_canonical", nullable = false)
    Boolean canonical = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    AiKnowledgeStatus status = AiKnowledgeStatus.DRAFT;
}

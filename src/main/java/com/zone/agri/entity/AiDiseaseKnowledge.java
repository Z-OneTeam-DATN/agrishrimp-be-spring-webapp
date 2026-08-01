package com.zone.agri.entity;

import com.zone.agri.entity.enums.AiKnowledgeStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_disease_knowledge",
        indexes = {
                @Index(name = "idx_ai_disease_knowledge_code", columnList = "code"),
                @Index(name = "idx_ai_disease_knowledge_status", columnList = "status"),
                @Index(name = "idx_ai_disease_knowledge_enabled", columnList = "enabled")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiDiseaseKnowledge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true, length = 120)
    String code;

    @Column(name = "name_vi", nullable = false, length = 255)
    String nameVi;

    @Column(name = "name_en", length = 255)
    String nameEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    AiKnowledgeCategory category;

    @Column(name = "aliases_raw", columnDefinition = "TEXT")
    String aliasesRaw;

    @Column(name = "symptom_keywords_raw", columnDefinition = "TEXT")
    String symptomKeywordsRaw;

    @Column(name = "signs_summary", columnDefinition = "LONGTEXT")
    String signsSummary;

    @Column(name = "causes_json", columnDefinition = "LONGTEXT")
    String causesJson;

    @Column(name = "treatment_stages_json", columnDefinition = "LONGTEXT")
    String treatmentStagesJson;

    /** JSON array URL anh minh hoa benh (Cloudinary) — hien kem phac do khi AI tra loi chat. */
    @Column(name = "image_urls_json", columnDefinition = "TEXT")
    String imageUrlsJson;

    /** Tên kỹ sư/đội ngũ đứng sau tri thức bệnh này — hiển thị làm nguồn khi AI trả lời chat. */
    @Column(name = "engineer_name", length = 150)
    String engineerName;

    /** SĐT liên hệ khẩn cấp gắn với đúng bệnh này — hiển thị kèm phác đồ khi AI trả lời chat. */
    @Column(name = "engineer_phone", length = 30)
    String engineerPhone;

    @Builder.Default
    @Column(name = "confidence_threshold", nullable = false)
    Double confidenceThreshold = 0.65d;

    @Builder.Default
    @Column(name = "match_threshold", nullable = false)
    Double matchThreshold = 0.40d;

    @Builder.Default
    @Column(nullable = false)
    Boolean enabled = true;

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

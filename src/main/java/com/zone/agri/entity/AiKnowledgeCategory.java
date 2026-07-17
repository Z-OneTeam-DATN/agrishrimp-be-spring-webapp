package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_knowledge_categories",
        indexes = {
                @Index(name = "idx_ai_knowledge_categories_slug", columnList = "slug")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiKnowledgeCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 150)
    String name;

    @Column(nullable = false, unique = true, length = 180)
    String slug;

    @Column(columnDefinition = "TEXT")
    String description;

    @Builder.Default
    @Column(nullable = false)
    Boolean enabled = true;

    @Builder.Default
    @Column(nullable = false)
    Integer sortOrder = 0;
}

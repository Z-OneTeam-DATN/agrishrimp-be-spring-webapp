package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_knowledge_chat_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiKnowledgeChatConfig extends BaseEntity {

    @Id
    Long id;

    @Column(name = "greeting_message", columnDefinition = "LONGTEXT")
    String greetingMessage;

    @Column(name = "fallback_message", columnDefinition = "LONGTEXT")
    String fallbackMessage;
}

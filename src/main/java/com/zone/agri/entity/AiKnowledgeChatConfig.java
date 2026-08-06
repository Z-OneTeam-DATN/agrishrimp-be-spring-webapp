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

    /** Ten ky su lien he mac dinh khi AI tu van tu do (khong khop benh nao trong kho tri thuc). */
    @Column(name = "fallback_contact_name", length = 150)
    String fallbackContactName;

    /** SDT ky su lien he mac dinh khi AI tu van tu do (khong khop benh nao trong kho tri thuc). */
    @Column(name = "fallback_contact_phone", length = 30)
    String fallbackContactPhone;
}

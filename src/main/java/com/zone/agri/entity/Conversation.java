package com.zone.agri.entity;

import com.zone.agri.entity.enums.ConversationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Conversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User customer;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('OPEN','CLOSED')", nullable = false)
    @Builder.Default
    ConversationStatus status = ConversationStatus.OPEN;

    @Column(name = "last_message", columnDefinition = "TEXT")
    String lastMessage;

    @Column(name = "last_message_at")
    LocalDateTime lastMessageAt;

    @Column(name = "last_sender_id")
    Long lastSenderId;

    @Column(name = "unread_by_shop")
    @Builder.Default
    Integer unreadByShop = 0;

    @Column(name = "unread_by_customer")
    @Builder.Default
    Integer unreadByCustomer = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User assignedStaff;

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<ChatMessage> messages;
}

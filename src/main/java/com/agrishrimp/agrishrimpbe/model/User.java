package com.agrishrimp.agrishrimpbe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;


    @Column(name = "full_name", nullable = false, length = 50)
    private String fullName;

    @Column(name = "phone_number", unique = true, length = 15)
    private String phoneNumber;

    @Column(name = "email", unique = true, length = 100)
    private String email;


    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    // 0: Nữ | 1: Nam | 2: Khác
    @Column(name = "gender")
    private Integer gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;


    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "is_newsletter_subscribed")
    private Boolean isNewsletterSubscribed;


    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    @Column(name = "auth_provider", length = 20)
    private String authProvider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "is_deleted")
    private Boolean isDeleted;


    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    public enum Status {
        ACTIVE,
        INACTIVE,
        BANNED,
        UNVERIFIED
    }
}

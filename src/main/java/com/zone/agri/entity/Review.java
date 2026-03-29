package com.zone.agri.entity;

import com.zone.agri.entity.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    // Tinyint mapped to Integer or Byte. Integer is safer.
    @Column(name = "rating")
    Integer rating;

    @Column(name = "comment", columnDefinition = "TEXT")
    String comment;

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    String adminReply;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('VISIBLE', 'HIDDEN')")
    ReviewStatus status;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Product product;

    @OneToMany(mappedBy = "review", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @Builder.Default
    List<ReviewImage> reviewImages = new java.util.ArrayList<>();
}
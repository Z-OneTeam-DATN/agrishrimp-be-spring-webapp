package com.zone.agri.entity;

import com.zone.agri.entity.enums.ReviewMediaType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "review_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "media_url", length = 255)
    String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", columnDefinition = "ENUM('IMAGE', 'VIDEO')")
    ReviewMediaType mediaType;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Review review;
}

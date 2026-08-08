package com.zone.agri.entity;

import com.zone.agri.entity.enums.BlogPostStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "blog_posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "title", length = 255, nullable = false)
    String title;

    @Column(name = "slug", length = 255, unique = true, nullable = false)
    String slug;

    @Column(name = "excerpt", columnDefinition = "TEXT")
    String excerpt;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    String content;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    String thumbnailUrl;

    @Column(name = "thumbnail_public_id", length = 255)
    String thumbnailPublicId;

    @Column(name = "seo_title", length = 255)
    String seoTitle;

    @Column(name = "meta_description", length = 320)
    String metaDescription;

    @Column(name = "canonical_url", length = 500)
    String canonicalUrl;

    /** Admin-only editorial note. Do not render as meta keywords. */
    @Column(name = "focus_keyword", length = 255)
    String focusKeyword;

    @Column(name = "cover_image_alt", length = 255)
    String coverImageAlt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    BlogPostStatus status;

    @Column(name = "view_count")
    @Builder.Default
    Long viewCount = 0L;

    @Column(name = "published_at")
    LocalDateTime publishedAt;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    /** Ly do tu choi / yeu cau chinh sua tu admin — hien cho tac gia xem tren form sua. Null khi chua tung bi tu choi. */
    @Column(name = "review_note", columnDefinition = "TEXT")
    String reviewNote;

    // --- Quan hệ ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    BlogCategory category;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "blog_post_tags",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    Set<BlogTag> tags = new HashSet<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    List<BlogPostProduct> relatedProducts = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    List<BlogComment> comments = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = BlogPostStatus.DRAFT;
        if (viewCount == null) viewCount = 0L;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}

package com.zone.agri.entity;

import com.zone.agri.entity.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "name", length = 255)
    String name;

    @Column(name = "slug", length = 255, unique = true)
    String slug;

    @Column(name = "short_desc", length = 500)
    String shortDesc;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    ProductStatus status;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    @Column(name = "rating_average")
    Float ratingAverage;

    @Column(name = "review_count")
    Integer reviewCount;

    @Column(name = "origin", length = 100)
    String origin;

    @Column(name = "base_sku", length = 50)
    String baseSku;

    // --- KHÓA NGOẠI ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<ProductImage> productImages;
}
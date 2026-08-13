package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "image_url", columnDefinition = "TEXT")
    String imageUrl;

    /** Public ID Cloudinary – dùng để xóa / cập nhật ảnh sau này */
    @Column(name = "public_id", length = 255)
    String publicId;

    @Column(name = "name", length = 255)
    String name;

    @Column(name = "text", length = 100)
    String text;

    @JsonIgnore  // phá vòng lặp: Product → productImages → product → productImages → ...
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Product product;
}
package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Lưu trạng thái index ảnh của từng sản phẩm vào AI service.
 * Mỗi sản phẩm có tối đa 1 bản ghi (product_id UNIQUE).
 */
@Entity
@Table(name = "product_vectors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    Long productId;

    /** URL ảnh đã gửi sang AI service lần gần nhất */
    @Column(name = "image_url", columnDefinition = "TEXT")
    String imageUrl;

    @Column(name = "indexed_at")
    LocalDateTime indexedAt;

    /** Embedding vector từ Python AI service (JSON array). Null khi chưa xử lý xong. */
    @Column(name = "vector", columnDefinition = "LONGTEXT")
    String vector;

    /** Tự động cập nhật mỗi khi record thay đổi */
    @UpdateTimestamp
    @Column(name = "updated_at", insertable = false, updatable = false)
    LocalDateTime updatedAt;
}

package com.zone.agri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_recommendations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_product_recommendation_pair",
                columnNames = {"product_id", "recommended_product_id"}
        ),
        indexes = {
                @Index(name = "idx_product_recommendations_product", columnList = "product_id"),
                @Index(name = "idx_product_recommendations_rank", columnList = "product_id, lift, confidence")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "product_id", nullable = false)
    Long productId;

    @Column(name = "recommended_product_id", nullable = false)
    Long recommendedProductId;

    @Column(name = "support_count", nullable = false)
    Integer supportCount;

    @Column(name = "customer_count", nullable = false)
    Integer customerCount;

    @Column(name = "support", precision = 12, scale = 6, nullable = false)
    BigDecimal support;

    @Column(name = "confidence", precision = 12, scale = 6, nullable = false)
    BigDecimal confidence;

    @Column(name = "lift", precision = 12, scale = 6, nullable = false)
    BigDecimal lift;

    @Column(name = "calculated_at", nullable = false)
    LocalDateTime calculatedAt;
}

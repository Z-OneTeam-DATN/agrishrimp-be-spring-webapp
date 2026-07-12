package com.zone.agri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_recommendation_clicks",
        indexes = {
                @Index(name = "idx_recommendation_clicks_product", columnList = "product_id"),
                @Index(name = "idx_recommendation_clicks_recommended", columnList = "recommended_product_id"),
                @Index(name = "idx_recommendation_clicks_clicked_at", columnList = "clicked_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRecommendationClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "product_id", nullable = false)
    Long productId;

    @Column(name = "recommended_product_id", nullable = false)
    Long recommendedProductId;

    @Column(name = "source", length = 80)
    String source;

    @Column(name = "clicked_at", nullable = false)
    LocalDateTime clickedAt;
}

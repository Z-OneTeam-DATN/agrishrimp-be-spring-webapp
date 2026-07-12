package com.zone.agri.repository;

import com.zone.agri.entity.ProductRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRecommendationRepository extends JpaRepository<ProductRecommendation, Long> {

    List<ProductRecommendation> findByProductIdOrderByLiftDescConfidenceDesc(
            Long productId,
            Pageable pageable
    );
}

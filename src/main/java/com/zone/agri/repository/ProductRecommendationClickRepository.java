package com.zone.agri.repository;

import com.zone.agri.entity.ProductRecommendationClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRecommendationClickRepository extends JpaRepository<ProductRecommendationClick, Long> {
}

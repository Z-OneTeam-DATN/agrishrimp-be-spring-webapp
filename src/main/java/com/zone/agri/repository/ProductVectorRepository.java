package com.zone.agri.repository;

import com.zone.agri.entity.ProductVector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductVectorRepository extends JpaRepository<ProductVector, Long> {
    Optional<ProductVector> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}

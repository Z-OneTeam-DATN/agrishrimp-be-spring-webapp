package com.zone.agri.repository;

import com.zone.agri.entity.ProductVariant;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.sku = :sku")
    Optional<ProductVariant> findBySku(@Param("sku") String sku);
}
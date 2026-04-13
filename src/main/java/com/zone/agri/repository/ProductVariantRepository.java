package com.zone.agri.repository;

import com.zone.agri.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.sku = :sku")
    Optional<ProductVariant> findBySku(@Param("sku") String sku);

    @Query("SELECT DISTINCT v FROM ProductVariant v JOIN FETCH v.product p " +
            "LEFT JOIN Inventory i ON i.productVariant.id = v.id " +
            "WHERE (v.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE) " +
            "AND (:keyword IS NULL OR :keyword = '' " +
            "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(v.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(v.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(i.batchNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ProductVariant> findAllActiveWithProduct(@Param("keyword") String keyword);
}

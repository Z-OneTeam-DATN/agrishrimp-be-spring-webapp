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

    // [CẬP NHẬT QUAN TRỌNG]: Trả về Entity thay vì DTO, để Service xử lý tồn kho
    @Query("SELECT v FROM ProductVariant v JOIN v.product p " +
            "WHERE (:keyword IS NULL OR :keyword = '' " +
            "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(v.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(v.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ProductVariant> searchByKeyword(@Param("keyword") String keyword);
}
package com.zone.agri.repository;



import com.zone.agri.dto.product.VariantSearchResponse;
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

    // Tìm kiếm sản phẩm theo Tên (từ bảng Product), SKU, hoặc Barcode
    @Query("SELECT new com.zone.agri.dto.product.VariantSearchResponse(" +
            "v.id, v.sku, v.barcode, p.name, '', v.quantity) " +
            "FROM ProductVariant v JOIN v.product p " +
            "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(v.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(v.barcode) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<VariantSearchResponse> searchVariants(@Param("keyword") String keyword);
}
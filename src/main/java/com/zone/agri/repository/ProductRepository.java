package com.zone.agri.repository;

import com.zone.agri.entity.Brand;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Fetch brand + category + productImages trong 1 query, tránh N+1
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.brand " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.productImages " +
           "LEFT JOIN FETCH p.variants")
    List<Product> findAllWithDetails();

    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.brand b " +
           "LEFT JOIN FETCH p.category c " +
           "LEFT JOIN FETCH p.productImages " +
           "LEFT JOIN FETCH p.variants v " +
           "WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "      OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "      OR LOWER(v.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryId IS NULL OR c.id = :categoryId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "ORDER BY p.createdAt DESC")
    List<Product> findAllWithFilter(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("status") ProductStatus status);

    // --- LOGIC XÓA DỮ LIỆU CON TRƯỚC KHI XÓA CHA ---

    @Modifying
    @Query("DELETE FROM SKUAttributeValue sav WHERE sav.sku.product = :product")
    void deleteVariantAttributesByProduct(@Param("product") Product product);

    @Modifying
    @Query("DELETE FROM UnitConversion uc WHERE uc.variant.product = :product")
    void deleteUnitConversionsByProduct(@Param("product") Product product);

    @Modifying
    @Query("DELETE FROM ProductVariant pv WHERE pv.product = :product")
    void deleteVariantsByProduct(@Param("product") Product product);

    @Modifying
    @Query("DELETE FROM ProductImage pi WHERE pi.product = :product")
    void deleteImagesByProduct(@Param("product") Product product);

    // --- LOGIC TÌM KIẾM BẢNG PHỤ ---

    @Query("SELECT b FROM Brand b WHERE LOWER(b.name) = LOWER(:name)")
    Optional<Brand> findBrandByName(@Param("name") String name);
}
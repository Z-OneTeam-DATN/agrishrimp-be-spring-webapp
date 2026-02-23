package com.zone.agri.repository;

import com.zone.agri.entity.Brand;
import com.zone.agri.entity.Product;
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
           "LEFT JOIN FETCH p.productImages")
    List<Product> findAllWithDetails();

    // --- LOGIC XÓA DỮ LIỆU CON TRƯỚC KHI XÓA CHA ---

    @Modifying
    @Query("DELETE FROM VariantAttribute va WHERE va.variant.product = :product")
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
package com.zone.agri.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zone.agri.entity.Brand;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.enums.ProductStatus;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

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

  @Modifying
  @Query("DELETE FROM SKUAttributeValue sav WHERE sav.sku.product = :product")
  void deleteVariantAttributesByProduct(@Param("product") Product product);

  @Modifying
  @Query("DELETE FROM ProductVariant pv WHERE pv.product = :product")
  void deleteVariantsByProduct(@Param("product") Product product);

  @Modifying
  @Query("DELETE FROM ProductImage pi WHERE pi.product = :product")
  void deleteImagesByProduct(@Param("product") Product product);

  @Query("SELECT b FROM Brand b WHERE LOWER(b.name) = LOWER(:name)")
  Optional<Brand> findBrandByName(@Param("name") String name);

  @Query("SELECT DISTINCT p FROM Product p " +
      "LEFT JOIN FETCH p.productImages " +
      "LEFT JOIN FETCH p.category " +
      "WHERE p.status = :status " +
      "ORDER BY p.name ASC")
  List<Product> findActiveProductsForAiDoctor(@Param("status") ProductStatus status);

  @Query("SELECT p FROM Product p " +
      "JOIN p.category c " +
      "JOIN p.variants v " +
      "JOIN Inventory i ON i.productVariant.id = v.id " +
      "WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE' " +
      "AND v.status = 'ACTIVE' " +
      "AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE " +
      "GROUP BY p.id " +
      "HAVING SUM(i.quantity) > 0")
  List<Product> findProductsForSale();



  @Query("""
      SELECT p.id, SUM(oi.quantity)
      FROM OrderItem oi
      JOIN oi.order o
      JOIN oi.productVariant pv
      JOIN pv.product p
      WHERE p.id IN :productIds
        AND o.status = com.zone.agri.entity.enums.OrderStatus.COMPLETED
        AND o.subOrders IS EMPTY
      GROUP BY p.id
      """)
  List<Object[]> sumLegacySoldQuantityByProductIds(@Param("productIds") List<Long> productIds);

  @Query("""
      SELECT p.id, SUM(si.quantity)
      FROM SubOrderItem si
      JOIN si.subOrder s
      JOIN si.productVariant pv
      JOIN pv.product p
      WHERE p.id IN :productIds
        AND s.status = com.zone.agri.entity.enums.OrderStatus.COMPLETED
      GROUP BY p.id
      """)
  List<Object[]> sumSubOrderSoldQuantityByProductIds(@Param("productIds") List<Long> productIds);

  Optional<Product> findBySlug(String slug);

  @Query("""
      SELECT DISTINCT p FROM Product p
      LEFT JOIN FETCH p.brand
      LEFT JOIN FETCH p.category
      LEFT JOIN FETCH p.productImages
      WHERE p.slug = :slug
      """)
  Optional<Product> findBySlugWithPublicDetails(@Param("slug") String slug);

  @Query(value = """
      SELECT p.id FROM Product p
      JOIN p.category c
      LEFT JOIN p.brand b
      WHERE p.status = com.zone.agri.entity.enums.ProductStatus.ACTIVE
        AND c.status = com.zone.agri.entity.enums.CategoryStatus.ACTIVE
        AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:categoryId IS NULL OR c.id = :categoryId OR c.parent.id = :categoryId)
        AND (:brandId IS NULL OR b.id = :brandId)
        AND EXISTS (
          SELECT 1 FROM ProductVariant v
          JOIN Inventory i ON i.productVariant.id = v.id
          WHERE v.product.id = p.id
            AND v.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE
            AND NOT EXISTS (
              SELECT 1 FROM SKUAttributeValue sav
              WHERE sav.sku.id = v.id
                AND sav.attribute.status = com.zone.agri.entity.enums.AttributeStatus.INACTIVE
            )
            AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE
            AND i.quantity > 0
        )
      ORDER BY p.createdAt DESC
      """, countQuery = """
      SELECT COUNT(p) FROM Product p
      JOIN p.category c
      LEFT JOIN p.brand b
      WHERE p.status = com.zone.agri.entity.enums.ProductStatus.ACTIVE
        AND c.status = com.zone.agri.entity.enums.CategoryStatus.ACTIVE
        AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:categoryId IS NULL OR c.id = :categoryId OR c.parent.id = :categoryId)
        AND (:brandId IS NULL OR b.id = :brandId)
        AND EXISTS (
          SELECT 1 FROM ProductVariant v
          JOIN Inventory i ON i.productVariant.id = v.id
          WHERE v.product.id = p.id
            AND v.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE
            AND NOT EXISTS (
              SELECT 1 FROM SKUAttributeValue sav
              WHERE sav.sku.id = v.id
                AND sav.attribute.status = com.zone.agri.entity.enums.AttributeStatus.INACTIVE
            )
            AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE
            AND i.quantity > 0
        )
      """)
  Page<Long> findPublicProductIds(
      @Param("keyword") String keyword,
      @Param("categoryId") Long categoryId,
      @Param("brandId") Long brandId,
      Pageable pageable);

  @Query(value = """
      SELECT p.id FROM Product p
      JOIN p.category c
      LEFT JOIN p.brand b
      WHERE p.status = com.zone.agri.entity.enums.ProductStatus.ACTIVE
        AND c.status = com.zone.agri.entity.enums.CategoryStatus.ACTIVE
        AND (
          :keyword IS NULL
          OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(COALESCE(b.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR (:hasKeywordCategoryFilter = true AND c.id IN :keywordCategoryIds)
          OR (:hasKeywordBrandFilter = true AND b.id IN :keywordBrandIds)
          OR EXISTS (
            SELECT 1 FROM ProductVariant kv
            WHERE kv.product.id = p.id
              AND (
                LOWER(kv.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(kv.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
          )
        )
        AND (:hasCategoryFilter = false OR c.id IN :categoryIds)
        AND (:brandId IS NULL OR b.id = :brandId)
        AND EXISTS (
          SELECT 1 FROM ProductVariant v
          JOIN Inventory i ON i.productVariant.id = v.id
          WHERE v.product.id = p.id
            AND v.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE
            AND (:hasPackagingFilter = false OR EXISTS (
              SELECT 1 FROM SKUAttributeValue savp
              WHERE savp.sku.id = v.id
                AND savp.attribute.status = com.zone.agri.entity.enums.AttributeStatus.ACTIVE
                AND (
                  (:hasPackagingValueIdFilter = true AND savp.attributeValue.id IN :packagingValueIds)
                  OR LOWER(savp.attributeValue.value) IN :packagingValues
                )
            ))
            AND NOT EXISTS (
              SELECT 1 FROM SKUAttributeValue sav
              WHERE sav.sku.id = v.id
                AND sav.attribute.status = com.zone.agri.entity.enums.AttributeStatus.INACTIVE
            )
            AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE
            AND i.quantity > 0
        )
      ORDER BY p.createdAt DESC
      """, countQuery = """
      SELECT COUNT(p) FROM Product p
      JOIN p.category c
      LEFT JOIN p.brand b
      WHERE p.status = com.zone.agri.entity.enums.ProductStatus.ACTIVE
        AND c.status = com.zone.agri.entity.enums.CategoryStatus.ACTIVE
        AND (
          :keyword IS NULL
          OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(COALESCE(b.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR (:hasKeywordCategoryFilter = true AND c.id IN :keywordCategoryIds)
          OR (:hasKeywordBrandFilter = true AND b.id IN :keywordBrandIds)
          OR EXISTS (
            SELECT 1 FROM ProductVariant kv
            WHERE kv.product.id = p.id
              AND (
                LOWER(kv.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(kv.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
          )
        )
        AND (:hasCategoryFilter = false OR c.id IN :categoryIds)
        AND (:brandId IS NULL OR b.id = :brandId)
        AND EXISTS (
          SELECT 1 FROM ProductVariant v
          JOIN Inventory i ON i.productVariant.id = v.id
          WHERE v.product.id = p.id
            AND v.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE
            AND (:hasPackagingFilter = false OR EXISTS (
              SELECT 1 FROM SKUAttributeValue savp
              WHERE savp.sku.id = v.id
                AND savp.attribute.status = com.zone.agri.entity.enums.AttributeStatus.ACTIVE
                AND (
                  (:hasPackagingValueIdFilter = true AND savp.attributeValue.id IN :packagingValueIds)
                  OR LOWER(savp.attributeValue.value) IN :packagingValues
                )
            ))
            AND NOT EXISTS (
              SELECT 1 FROM SKUAttributeValue sav
              WHERE sav.sku.id = v.id
                AND sav.attribute.status = com.zone.agri.entity.enums.AttributeStatus.INACTIVE
            )
            AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE
            AND i.quantity > 0
        )
      """)
  Page<Long> findPublicProductIdsFiltered(
      @Param("keyword") String keyword,
      @Param("hasKeywordCategoryFilter") boolean hasKeywordCategoryFilter,
      @Param("keywordCategoryIds") List<Long> keywordCategoryIds,
      @Param("hasKeywordBrandFilter") boolean hasKeywordBrandFilter,
      @Param("keywordBrandIds") List<Long> keywordBrandIds,
      @Param("hasCategoryFilter") boolean hasCategoryFilter,
      @Param("categoryIds") List<Long> categoryIds,
      @Param("brandId") Long brandId,
      @Param("hasPackagingFilter") boolean hasPackagingFilter,
      @Param("hasPackagingValueIdFilter") boolean hasPackagingValueIdFilter,
      @Param("packagingValueIds") List<Long> packagingValueIds,
      @Param("packagingValues") List<String> packagingValues,
      Pageable pageable);

  @Query("""
      SELECT DISTINCT p FROM Product p
      LEFT JOIN FETCH p.brand
      LEFT JOIN FETCH p.category
      LEFT JOIN FETCH p.productImages
      LEFT JOIN FETCH p.variants v
      LEFT JOIN FETCH v.attributeValues sav
      LEFT JOIN FETCH sav.attribute
      LEFT JOIN FETCH sav.attributeValue
      WHERE p.id IN :ids
      """)
  List<Product> findPublicByIds(@Param("ids") List<Long> ids);

  long countByCategoryId(Long categoryId);

  @Modifying
  @Query("UPDATE Product p SET p.status = 'INACTIVE' WHERE p.category.id = :categoryId")
  void deactivateByCategoryId(@Param("categoryId") Long categoryId);

  @Modifying
  @Query("UPDATE Product p SET p.status = 'ACTIVE' WHERE p.category.id = :categoryId")
  void activateByCategoryId(@Param("categoryId") Long categoryId);

  boolean existsByCategoryId(Long categoryId);

  boolean existsByBrandId(Long brandId);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.status = 'ACTIVE'")
  long countActiveProducts();

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

  interface CategorySalesProjection {
    Long getCategoryId();

    String getCategoryName();

    BigDecimal getTotalRevenue();

    Long getTotalQuantity();
  }

  @Query("SELECT c.id AS categoryId, c.name AS categoryName, " +
      "SUM(oi.price * oi.quantity) AS totalRevenue, " +
      "SUM(oi.quantity) AS totalQuantity " +
      "FROM OrderItem oi " +
      "JOIN oi.productVariant pv " +
      "JOIN pv.product p " +
      "JOIN p.category c " +
      "JOIN oi.order o " +
      "WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
      +
      "AND o.subOrders IS EMPTY " +
      "AND (:branchId IS NULL OR o.branch.id = :branchId) " +
      "GROUP BY c.id, c.name " +
      "ORDER BY totalRevenue DESC")
  List<CategorySalesProjection> getCategorySalesLegacy(@Param("branchId") Long branchId);

  interface TopProductProjection {
    Long getProductId();

    String getProductName();

    Long getQuantitySold();

    BigDecimal getRevenue();

    String getImageUrl();
  }

  @Query("SELECT p.id AS productId, p.name AS productName, " +
      "SUM(si.quantity) AS quantitySold, " +
      "SUM(si.unitPrice * si.quantity) AS revenue, " +
      "MAX(pv.imageUrl) AS imageUrl " +
      "FROM SubOrderItem si " +
      "JOIN si.subOrder s " +
      "JOIN si.productVariant pv " +
      "JOIN pv.product p " +
      "WHERE s.status = com.zone.agri.entity.enums.OrderStatus.COMPLETED "
      +
      "AND (:branchId IS NULL OR s.branch.id = :branchId) " +
      "GROUP BY p.id, p.name " +
      "ORDER BY quantitySold DESC")
  List<TopProductProjection> getTopSellingProducts(@Param("branchId") Long branchId);

  @Query("SELECT p.id AS productId, p.name AS productName, " +
      "SUM(oi.quantity) AS quantitySold, " +
      "SUM(oi.price * oi.quantity) AS revenue, " +
      "MAX(pv.imageUrl) AS imageUrl " +
      "FROM OrderItem oi " +
      "JOIN oi.order o " +
      "JOIN oi.productVariant pv " +
      "JOIN pv.product p " +
      "WHERE o.status = com.zone.agri.entity.enums.OrderStatus.COMPLETED "
      +
      "AND o.subOrders IS EMPTY " +
      "AND (:branchId IS NULL OR o.branch.id = :branchId) " +
      "GROUP BY p.id, p.name " +
      "ORDER BY quantitySold DESC")
  List<TopProductProjection> getTopSellingProductsLegacy(@Param("branchId") Long branchId);
}


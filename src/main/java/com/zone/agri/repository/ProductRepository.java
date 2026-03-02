package com.zone.agri.repository;

import com.zone.agri.entity.Brand;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
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


    // Lấy danh sách sản phẩm đang kinh doanh và còn hàng tại chi nhánh ACTIVE
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

    // Lấy Top bán chạy dựa trên số lượng trong OrderItem, chỉ tính chi nhánh ACTIVE
    @Query("SELECT p FROM Product p " +
            "JOIN p.category c " +
            "JOIN p.variants v " +
            "JOIN OrderItem oi ON oi.productVariant.id = v.id " +
            "JOIN Inventory i ON i.productVariant.id = v.id " +
            "WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE' " +
            "AND v.status = 'ACTIVE' " +
            "AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE " +
            "GROUP BY p.id " +
            "HAVING SUM(i.quantity) > 0 " +
            "ORDER BY SUM(oi.quantity) DESC")
    List<Product> findTopBestSellers(Pageable pageable);

    // Tìm theo Slug cho trang chi tiết
    Optional<Product> findBySlug(String slug);

    // =========================================================================
    // PUBLIC WEBSITE API — không lộ dữ liệu nội bộ
    // =========================================================================

    /**
     * Bước 1 (phân trang): trả về danh sách ID sản phẩm thoả điều kiện hiển thị công khai.
     * Dùng query riêng trên ID để tránh Hibernate in-memory pagination
     * khi fetch collection (variants).
     * Điều kiện:
     *  - product.status = ACTIVE
     *  - category.status = ACTIVE
     *  - (keyword trùng tên sản phẩm) AND (categoryId khớp nếu có)
     */
    @Query(value = """
            SELECT p.id FROM Product p
            JOIN p.category c
            LEFT JOIN p.brand b
            WHERE p.status = com.zone.agri.entity.enums.ProductStatus.ACTIVE
              AND c.status = com.zone.agri.entity.enums.CategoryStatus.ACTIVE
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:brandId IS NULL OR b.id = :brandId)
              AND EXISTS (
                SELECT 1 FROM ProductVariant v
                JOIN Inventory i ON i.productVariant.id = v.id
                WHERE v.product.id = p.id
                  AND v.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE
                  AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE
                  AND i.quantity > 0
              )
            ORDER BY p.createdAt DESC
            """,
           countQuery = """
            SELECT COUNT(p) FROM Product p
            JOIN p.category c
            LEFT JOIN p.brand b
            WHERE p.status = com.zone.agri.entity.enums.ProductStatus.ACTIVE
              AND c.status = com.zone.agri.entity.enums.CategoryStatus.ACTIVE
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (:brandId IS NULL OR b.id = :brandId)
              AND EXISTS (
                SELECT 1 FROM ProductVariant v
                JOIN Inventory i ON i.productVariant.id = v.id
                WHERE v.product.id = p.id
                  AND v.status = com.zone.agri.entity.enums.VariantStatus.ACTIVE
                  AND i.branch.status = com.zone.agri.entity.enums.BranchStatus.ACTIVE
                  AND i.quantity > 0
              )
            """)
    Page<Long> findPublicProductIds(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId, // Thêm tham số brandId
            Pageable pageable);

    /**
     * Bước 2 (data loading): fetch đầy đủ quan hệ cho danh sách ID.
     * JOIN FETCH brand + category + productImages để tránh N+1.
     * variants được load qua EAGER trên entity.
     */
    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.brand
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH p.productImages
            WHERE p.id IN :ids
            """)
    List<Product> findPublicByIds(@Param("ids") List<Long> ids);

    // Đếm số lượng sản phẩm trực tiếp của 1 danh mục
    long countByCategoryId(Long categoryId);

    // Phương thức gây lỗi của bạn đây: Cập nhật trạng thái INACTIVE cho tất cả SP thuộc Category ID
    @Modifying
    @Query("UPDATE Product p SET p.status = 'INACTIVE' WHERE p.category.id = :categoryId")
    void deactivateByCategoryId(@Param("categoryId") Long categoryId);

    // Kiểm tra xem danh mục có sản phẩm không (phục vụ logic xóa)
    boolean existsByCategoryId(Long categoryId);
}
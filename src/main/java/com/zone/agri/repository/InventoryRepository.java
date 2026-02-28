package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // ==============================
    // TÌM TỒN KHO THEO CHI NHÁNH + VARIANT
    // ==============================
    Optional<Inventory> findByBranchAndProductVariant(Branch branch, ProductVariant variant);

    Optional<Inventory> findByBranchIdAndProductVariantId(Long branchId, Long variantId);

    List<Inventory> findByProductVariantIdIn(List<Long> variantIds);

    Optional<Inventory> findByBranchIdAndProductVariantSku(Long branchId, String sku);

    // 👉 HÀM MỚI THÊM CHO PRODUCT SERVICE (Tìm tất cả lô hàng của 1 Variant)
    List<Inventory> findByProductVariantId(Long variantId);

    // ==============================
    // TỔNG TỒN KHO TOÀN HỆ THỐNG THEO PRODUCT
    // ==============================
    @Query("""
           SELECT COALESCE(SUM(i.quantity), 0L)
           FROM Inventory i
           WHERE i.productVariant.product.id = :productId
           """)
    Long sumQuantityByProductId(@Param("productId") Long productId);

    // ==============================
    // KIỂM TRA SẢN PHẨM CÓ TỒN KHO KHÔNG
    // ==============================
    boolean existsByProductVariantProductId(Long productId);

    // ==============================
    // BATCH: tổng tồn kho cho nhiều sản phẩm (dùng cho API public list)
    // ==============================
    @Query("""
           SELECT i.productVariant.product.id, COALESCE(SUM(i.quantity), 0)
           FROM Inventory i
           WHERE i.productVariant.product.id IN :productIds
           GROUP BY i.productVariant.product.id
           """)
    List<Object[]> sumQuantityGroupByProductIds(@Param("productIds") List<Long> productIds);

    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.id = :variantId")
    Long sumQuantityByProductVariantId(@Param("variantId") Long variantId);

    // Lấy TỔNG tồn kho của 1 biến thể trên TOÀN HỆ THỐNG (Dành cho Admin)
    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.id = :variantId")
    Integer sumQuantityByVariantId(@Param("variantId") Long variantId);

    // ==============================
    // TÁCH ĐƠN THÔNG MINH
    // ==============================

    /**
     * Query gộp tồn kho nhiều chi nhánh + nhiều variant — 1 lần duy nhất.
     * 👉 ĐÃ THÊM: ORDER BY i.id ASC để lấy lô hàng cũ xuất trước (FIFO).
     */
    @Query("""
           SELECT i FROM Inventory i
           WHERE i.branch.id IN :branchIds
             AND i.productVariant.id IN :variantIds
             AND i.quantity > 0
           ORDER BY i.id ASC
           """)
    List<Inventory> findInventoryMatrix(
            @Param("branchIds") List<Long> branchIds,
            @Param("variantIds") List<Long> variantIds
    );

    /**
     * Dùng khi confirm đơn — lock để tránh race condition (overselling).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.id = :variantId AND i.quantity > 0 ORDER BY i.id ASC")
    List<Inventory> findForUpdateFIFO(
            @Param("branchId") Long branchId,
            @Param("variantId") Long variantId
    );
}
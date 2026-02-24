package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // ==============================
    // TÌM TỒN KHO THEO CHI NHÁNH + VARIANT
    // ==============================
    Optional<Inventory> findByBranchAndProductVariant(Branch branch, ProductVariant variant);

    Optional<Inventory> findByBranchIdAndProductVariantId(Long branchId, Long variantId);

    Optional<Inventory> findByBranchIdAndProductVariantSku(Long branchId, String sku);

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
}
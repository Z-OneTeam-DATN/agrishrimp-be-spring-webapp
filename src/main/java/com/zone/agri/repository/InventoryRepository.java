package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
// SỬA DÒNG NÀY: Dùng của springframework thay vì lettuce
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    // Tìm tồn kho theo branch + variant
    Optional<Inventory> findByBranchAndProductVariant(Branch branch, ProductVariant variant);

    // Tìm đến hàm này trong InventoryRepository
    @Query("SELECT COALESCE(CAST(SUM(i.quantity) AS integer), 0) FROM Inventory i WHERE i.productVariant.product.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") Long productId);

    // Tìm tồn kho theo chi nhánh + variant
    Optional<Inventory> findByBranchIdAndProductVariantId(Long branchId, Long variantId);
}


    // Tổng tồn của 1 sản phẩm (toàn hệ thống)
    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.productVariant.product.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") Long productId);


    // Kiểm tra sản phẩm có tồn kho không
    boolean existsByProductVariantProductId(Long productId);

    // ==============================
    // TỒN KHO THEO CHI NHÁNH
    // ==============================
    Optional<Inventory> findByBranchIdAndProductVariantSku(Long branchId, String sku);

    Optional<Inventory> findByBranchAndProductVariant(Branch branch, ProductVariant variant);
}


package com.zone.agri.repository;

import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    // Tìm tồn kho theo branch + variant
    Optional<Inventory> findByBranchAndProductVariant(Branch branch, ProductVariant variant);

    // Tổng số lượng tồn kho theo Product
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.productVariant.product.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") Long productId);

    // Tìm tồn kho theo chi nhánh + variant
    Optional<Inventory> findByBranchIdAndProductVariantId(Long branchId, Long variantId);
}

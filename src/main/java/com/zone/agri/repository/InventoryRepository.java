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
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.productVariant.product.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") Long productId);

    boolean existsByProductVariantProductId(Long productId);

    // --- CODE MỚI ---
    // Tìm bản ghi tồn kho của 1 biến thể tại 1 chi nhánh nhất định
    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.productVariant.sku = :sku")
    Optional<Inventory> findByBranchIdAndVariantSku(@Param("branchId") Long branchId, @Param("sku") String sku);

    // Kiểm tra tồn kho tại chi nhánh cụ thể
    @Query("SELECT i FROM Inventory i WHERE i.branch = :branch AND i.productVariant = :variant")
    Optional<Inventory> findByBranchAndProductVariant(@Param("branch") Branch branch, @Param("variant") ProductVariant variant);
}
package com.zone.agri.repository;

import com.zone.agri.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.productVariant.product.id = :productId")
    Integer sumQuantityByProductId(@Param("productId") Long productId);

    boolean existsByProductVariantProductId(Long productId);
}

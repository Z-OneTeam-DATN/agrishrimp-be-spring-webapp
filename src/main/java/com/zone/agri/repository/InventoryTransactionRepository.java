package com.zone.agri.repository;

import com.zone.agri.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    boolean existsByInventoryProductVariantProductId(Long productId);
}

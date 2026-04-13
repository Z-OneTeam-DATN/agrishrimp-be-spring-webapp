package com.zone.agri.repository;

import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.List;

import java.util.Optional;


@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    boolean existsByInventoryProductVariantProductId(Long productId);

    List<InventoryTransaction> findByReferenceCodeAndType(String referenceCode, TransactionType type);


    Optional<InventoryTransaction> findFirstByInventoryAndTypeOrderByCreatedAtAsc(Inventory inventory, TransactionType type);

}

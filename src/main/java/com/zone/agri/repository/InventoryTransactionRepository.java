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

    @org.springframework.data.jpa.repository.Query("""
            SELECT SUM(
                       ABS(tx.quantityChange) *
                       CASE
                           WHEN t.transferBusinessType = com.zone.agri.entity.enums.TransferBusinessType.INTERNAL_SALE
                               THEN COALESCE(d.unitTransferPrice, COALESCE(tx.inventory.importPrice, 0))
                           ELSE COALESCE(tx.inventory.importPrice, 0)
                       END
                   ),
                   SUM(ABS(tx.quantityChange))
            FROM InventoryTransfer t
            JOIN t.details d
            JOIN InventoryTransaction tx
              ON tx.referenceCode = t.transferCode
             AND tx.type = com.zone.agri.entity.enums.TransactionType.TRANSFER_OUT
             AND tx.inventory.productVariant.id = d.productVariant.id
            WHERE t.status = com.zone.agri.entity.enums.InventoryTransferStatus.COMPLETED
              AND t.toBranch.id = :branchId
              AND d.productVariant.id = :variantId
            """)
    Object[] summarizeCompletedInboundTransferCost(@org.springframework.data.repository.query.Param("branchId") Long branchId,
                                                   @org.springframework.data.repository.query.Param("variantId") Long variantId);

    Optional<InventoryTransaction> findFirstByInventoryAndTypeOrderByCreatedAtAsc(Inventory inventory, TransactionType type);

}

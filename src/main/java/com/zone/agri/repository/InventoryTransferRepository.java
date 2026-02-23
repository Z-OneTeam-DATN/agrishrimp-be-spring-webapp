package com.zone.agri.repository;

import com.zone.agri.entity.InventoryTransfer;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long> {
    @Query("SELECT COUNT(it) > 0 FROM InventoryTransfer it JOIN it.details detail WHERE it.status IN :statuses AND detail.productVariant.product.id = :productId")
    boolean existsByStatusInAndProductId(@Param("statuses") Collection<InventoryTransferStatus> statuses, @Param("productId") Long productId);
}

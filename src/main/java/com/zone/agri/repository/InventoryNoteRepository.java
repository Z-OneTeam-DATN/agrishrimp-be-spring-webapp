package com.zone.agri.repository;

import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface InventoryNoteRepository extends JpaRepository<InventoryNote, Long> {
    boolean existsByStatusIn(Collection<InventoryNoteStatus> statuses);

    @Query("SELECT COUNT(in) > 0 FROM InventoryNote in JOIN in.details detail WHERE in.status IN :statuses AND detail.productVariant.product.id = :productId")
    boolean existsByStatusInAndProductId(@Param("statuses") Collection<InventoryNoteStatus> statuses, @Param("productId") Long productId);
}

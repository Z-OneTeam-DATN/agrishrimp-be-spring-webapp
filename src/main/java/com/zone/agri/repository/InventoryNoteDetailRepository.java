package com.zone.agri.repository;

import com.zone.agri.entity.InventoryNoteDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryNoteDetailRepository extends JpaRepository<InventoryNoteDetail, Long> {
    boolean existsByProductVariantProductId(Long productId);
    void deleteByInventoryNoteId(Long inventoryNoteId);
}


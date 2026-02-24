package com.zone.agri.repository;

import com.zone.agri.entity.InventoryNoteDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryNoteDetailRepository extends JpaRepository<InventoryNoteDetail, Long> {

    boolean existsByProductVariantProductId(Long productId);

    List<InventoryNoteDetail> findByInventoryNoteId(Long inventoryNoteId);

    void deleteByInventoryNoteId(Long inventoryNoteId);
}

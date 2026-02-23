package com.zone.agri.repository;

import com.zone.agri.entity.InventoryNoteDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InventoryNoteDetailRepository extends JpaRepository<InventoryNoteDetail, Long> {
    boolean existsByProductVariantProductId(Long productId);

    // --- CODE MỚI ---
    // Lấy tất cả chi tiết của một phiếu nhập kho dựa trên ID của phiếu đó
    @Query("SELECT d FROM InventoryNoteDetail d WHERE d.inventoryNote.id = :noteId")
    List<InventoryNoteDetail> findAllByNoteId(@Param("noteId") Long noteId);
}
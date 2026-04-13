package com.zone.agri.repository;

import com.zone.agri.entity.InventoryNoteDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface InventoryNoteDetailRepository extends JpaRepository<InventoryNoteDetail, Long> {

    boolean existsByProductVariantProductId(Long productId);

    List<InventoryNoteDetail> findByInventoryNoteId(Long inventoryNoteId);

    void deleteByInventoryNoteId(Long inventoryNoteId);

    @Query("SELECT d FROM InventoryNoteDetail d " +
           "JOIN d.inventoryNote n " +
           "WHERE n.type = com.zone.agri.entity.enums.InventoryNoteType.IMPORT " +
           "AND n.supplier.id = :supplierId " +
           "AND d.productVariant.sku = :sku " +
           "AND d.batchNumber = :batchNumber " +
           "AND n.status = com.zone.agri.entity.enums.InventoryNoteStatus.COMPLETED " +
           "ORDER BY n.createdAt DESC")
    List<InventoryNoteDetail> findOriginalImportDetail(
            @Param("supplierId") Long supplierId, 
            @Param("sku") String sku, 
            @Param("batchNumber") String batchNumber
    );
}

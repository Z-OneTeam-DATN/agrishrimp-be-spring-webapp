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
    interface ReferenceCostProjection {
        String getReferenceCode();

        java.math.BigDecimal getCost();
    }

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

    // Biến động ròng (có dấu) của giá trị tồn kho trong 1 khoảng thời gian — quantityChange đã có
    // dấu sẵn (dương = nhập/hoàn/điều chuyển vào, âm = bán/hỏng/điều chuyển ra), và newBalance
    // được cập nhật theo từng giao dịch nên quantityChange chính là delta số lượng thực tế.
    // Dùng để suy ra "giá trị tồn kho hôm qua" = giá trị hôm nay - biến động ròng hôm nay.
    @org.springframework.data.jpa.repository.Query("""
            SELECT COALESCE(SUM(tx.quantityChange * COALESCE(tx.inventory.importPrice, 0)), 0)
            FROM InventoryTransaction tx
            WHERE tx.createdAt BETWEEN :start AND :end
              AND (:branchId IS NULL OR tx.inventory.branch.id = :branchId)
            """)
    java.math.BigDecimal sumNetValueChange(@org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
                                           @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
                                           @org.springframework.data.repository.query.Param("branchId") Long branchId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT tx.referenceCode AS referenceCode,
                   COALESCE(SUM(ABS(tx.quantityChange) * COALESCE(tx.inventory.importPrice, 0)), 0) AS cost
            FROM InventoryTransaction tx
            WHERE tx.type = com.zone.agri.entity.enums.TransactionType.SALE
              AND tx.referenceCode IN :referenceCodes
              AND (:branchId IS NULL OR tx.inventory.branch.id = :branchId)
            GROUP BY tx.referenceCode
            """)
    List<ReferenceCostProjection> sumSaleCostByReferenceCodes(
            @org.springframework.data.repository.query.Param("referenceCodes") java.util.Collection<String> referenceCodes,
            @org.springframework.data.repository.query.Param("branchId") Long branchId);

    // ==============================================================
    // SỔ KHO (Ledger) + BÁO CÁO XUẤT NHẬP TỒN — chỉ tính các loại giao dịch làm thay đổi tồn kho
    // vật lý thật sự (IMPORT/SALE/TRANSFER_IN/TRANSFER_OUT/ADJUSTMENT/RETURN/DAMAGED). Cố tình bỏ
    // ORDER_RESERVE/ORDER_RELEASE/CANCEL_RELEASE vì newBalance của các loại đó là số dư "tạm giữ"
    // (reservedQuantity), không phải tồn kho thực tế — trộn chung sẽ làm sai "tồn trước/tồn sau".
    // ==============================================================

    @org.springframework.data.jpa.repository.Query("""
            SELECT tx FROM InventoryTransaction tx
            JOIN FETCH tx.inventory i
            JOIN FETCH i.productVariant pv
            JOIN FETCH pv.product p
            JOIN FETCH i.branch b
            LEFT JOIN FETCH tx.createdBy u
            WHERE tx.type IN :types
              AND (:branchId IS NULL OR i.branch.id = :branchId)
              AND (:start IS NULL OR tx.createdAt >= :start)
              AND (:end IS NULL OR tx.createdAt <= :end)
            ORDER BY tx.createdAt DESC
            """)
    List<InventoryTransaction> findLedger(
            @org.springframework.data.repository.query.Param("types") java.util.Collection<TransactionType> types,
            @org.springframework.data.repository.query.Param("branchId") Long branchId,
            @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);

    interface MovementProjection {
        Long getVariantId();

        String getSku();

        String getProductName();

        Long getImportedQuantity();

        java.math.BigDecimal getImportedValue();

        Long getExportedQuantity();

        java.math.BigDecimal getExportedValue();
    }

    @org.springframework.data.jpa.repository.Query("""
            SELECT pv.id AS variantId,
                   pv.sku AS sku,
                   p.name AS productName,
                   COALESCE(SUM(CASE WHEN tx.quantityChange > 0 THEN tx.quantityChange ELSE 0 END), 0) AS importedQuantity,
                   COALESCE(SUM(CASE WHEN tx.quantityChange > 0 THEN tx.quantityChange * COALESCE(i.importPrice, 0) ELSE 0 END), 0) AS importedValue,
                   COALESCE(SUM(CASE WHEN tx.quantityChange < 0 THEN -tx.quantityChange ELSE 0 END), 0) AS exportedQuantity,
                   COALESCE(SUM(CASE WHEN tx.quantityChange < 0 THEN -tx.quantityChange * COALESCE(i.importPrice, 0) ELSE 0 END), 0) AS exportedValue
            FROM InventoryTransaction tx
            JOIN tx.inventory i
            JOIN i.productVariant pv
            JOIN pv.product p
            WHERE tx.type IN :types
              AND (:branchId IS NULL OR i.branch.id = :branchId)
              AND tx.createdAt BETWEEN :start AND :end
            GROUP BY pv.id, pv.sku, p.name
            """)
    List<MovementProjection> findMovementSummary(
            @org.springframework.data.repository.query.Param("types") java.util.Collection<TransactionType> types,
            @org.springframework.data.repository.query.Param("branchId") Long branchId,
            @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
            @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);

    // Dùng để lùi "tồn hiện tại" về đúng "tồn cuối kỳ tại endDate": tồn hiện tại trừ đi mọi biến
    // động xảy ra SAU endDate thì ra đúng tồn tại thời điểm endDate — xem giải thích đầy đủ ở
    // InventoryReportService#getIOSummary.
    @org.springframework.data.jpa.repository.Query("""
            SELECT pv.id AS variantId,
                   pv.sku AS sku,
                   p.name AS productName,
                   COALESCE(SUM(CASE WHEN tx.quantityChange > 0 THEN tx.quantityChange ELSE 0 END), 0) AS importedQuantity,
                   COALESCE(SUM(CASE WHEN tx.quantityChange > 0 THEN tx.quantityChange * COALESCE(i.importPrice, 0) ELSE 0 END), 0) AS importedValue,
                   COALESCE(SUM(CASE WHEN tx.quantityChange < 0 THEN -tx.quantityChange ELSE 0 END), 0) AS exportedQuantity,
                   COALESCE(SUM(CASE WHEN tx.quantityChange < 0 THEN -tx.quantityChange * COALESCE(i.importPrice, 0) ELSE 0 END), 0) AS exportedValue
            FROM InventoryTransaction tx
            JOIN tx.inventory i
            JOIN i.productVariant pv
            JOIN pv.product p
            WHERE tx.type IN :types
              AND (:branchId IS NULL OR i.branch.id = :branchId)
              AND tx.createdAt > :after
            GROUP BY pv.id, pv.sku, p.name
            """)
    List<MovementProjection> findMovementAfterDate(
            @org.springframework.data.repository.query.Param("types") java.util.Collection<TransactionType> types,
            @org.springframework.data.repository.query.Param("branchId") Long branchId,
            @org.springframework.data.repository.query.Param("after") java.time.LocalDateTime after);

}

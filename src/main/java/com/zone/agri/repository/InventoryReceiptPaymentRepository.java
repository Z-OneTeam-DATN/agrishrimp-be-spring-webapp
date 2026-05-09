package com.zone.agri.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zone.agri.entity.InventoryReceiptPayment;

@Repository
public interface InventoryReceiptPaymentRepository extends JpaRepository<InventoryReceiptPayment, Long> {

    @Query("""
        SELECT p
        FROM InventoryReceiptPayment p
        LEFT JOIN FETCH p.inventoryNote n
        LEFT JOIN FETCH p.supplier
        LEFT JOIN FETCH p.branch
        LEFT JOIN FETCH p.createdBy
        WHERE p.inventoryNote.id = :receiptId
        ORDER BY p.paymentDate DESC, p.id DESC
    """)
    List<InventoryReceiptPayment> findByReceiptIdWithDetails(@Param("receiptId") Long receiptId);

    @Query("""
        SELECT p
        FROM InventoryReceiptPayment p
        WHERE p.inventoryNote.id = :receiptId
        ORDER BY p.paymentDate ASC, p.id ASC
    """)
    List<InventoryReceiptPayment> findByReceiptIdOrderByPaymentDateAscIdAsc(@Param("receiptId") Long receiptId);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM InventoryReceiptPayment p
        WHERE p.inventoryNote.id = :receiptId
    """)
    BigDecimal sumAmountByReceiptId(@Param("receiptId") Long receiptId);

    @Query("""
        SELECT p
        FROM InventoryReceiptPayment p
        LEFT JOIN FETCH p.inventoryNote n
        LEFT JOIN FETCH p.supplier
        LEFT JOIN FETCH p.branch
        LEFT JOIN FETCH p.createdBy
        WHERE (:startDate IS NULL OR p.paymentDate >= :startDate)
          AND (:endDate IS NULL OR p.paymentDate <= :endDate)
          AND (:branchId IS NULL OR p.branch.id = :branchId)
        ORDER BY p.paymentDate DESC, p.id DESC
    """)
    List<InventoryReceiptPayment> findAllWithFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("branchId") Long branchId
    );

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM InventoryReceiptPayment p
        WHERE (:beforeDate IS NULL OR p.paymentDate < :beforeDate)
          AND (:branchId IS NULL OR p.branch.id = :branchId)
    """)
    BigDecimal sumAmountBeforeDate(
            @Param("beforeDate") LocalDateTime beforeDate,
            @Param("branchId") Long branchId
    );

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM InventoryReceiptPayment p
        WHERE (:startDate IS NULL OR p.paymentDate >= :startDate)
          AND (:endDate IS NULL OR p.paymentDate <= :endDate)
          AND (:branchId IS NULL OR p.branch.id = :branchId)
    """)
    BigDecimal sumAmountInRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("branchId") Long branchId
    );
}

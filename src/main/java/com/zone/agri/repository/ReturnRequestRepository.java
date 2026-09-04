package com.zone.agri.repository;

import com.zone.agri.entity.ReturnRequest;
import com.zone.agri.entity.enums.ReturnRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    @Query("""
            SELECT r
            FROM ReturnRequest r
            LEFT JOIN FETCH r.order o
            LEFT JOIN FETCH r.branch b
            LEFT JOIN FETCH r.receivedInventoryNote rin
            WHERE r.user.id = :userId
            ORDER BY r.createdAt DESC
            """)
    List<ReturnRequest> findAllForUser(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT r
            FROM ReturnRequest r
            LEFT JOIN FETCH r.order o
            LEFT JOIN FETCH r.branch b
            LEFT JOIN FETCH r.receivedInventoryNote rin
            LEFT JOIN FETCH r.items items
            LEFT JOIN FETCH r.evidences evidences
            WHERE r.user.id = :userId
            ORDER BY r.createdAt DESC
            """)
    List<ReturnRequest> findAllDetailedForUser(@Param("userId") Long userId);

    Optional<ReturnRequest> findTopByUserIdAndOrderIdOrderByCreatedAtDesc(Long userId, Long orderId);

    boolean existsByOrderId(Long orderId);

    @Query("""
            SELECT DISTINCT r.order.id
            FROM ReturnRequest r
            WHERE r.order.id IN :orderIds
            """)
    Set<Long> findOrderIdsWithRequests(@Param("orderIds") List<Long> orderIds);

    @Query("""
            SELECT DISTINCT r
            FROM ReturnRequest r
            LEFT JOIN FETCH r.order o
            LEFT JOIN FETCH r.branch b
            LEFT JOIN FETCH r.receivedInventoryNote rin
            LEFT JOIN FETCH r.items items
            LEFT JOIN FETCH r.evidences evidences
            WHERE r.id = :id
            """)
    Optional<ReturnRequest> findDetailedById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT r
            FROM ReturnRequest r
            LEFT JOIN FETCH r.order o
            LEFT JOIN FETCH r.branch b
            LEFT JOIN FETCH r.receivedInventoryNote rin
            LEFT JOIN FETCH r.items items
            LEFT JOIN FETCH r.evidences evidences
            WHERE r.id = :id
              AND r.user.id = :userId
            """)
    Optional<ReturnRequest> findDetailedByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            SELECT r
            FROM ReturnRequest r
            LEFT JOIN FETCH r.order o
            LEFT JOIN FETCH r.branch b
            LEFT JOIN FETCH r.receivedInventoryNote rin
            WHERE (:branchId IS NULL OR b.id = :branchId)
              AND (:status IS NULL OR r.status = :status)
              AND (
                  :search IS NULL OR TRIM(:search) = '' OR
                  LOWER(r.code) LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(o.code) LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(r.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                  LOWER(r.customerPhone) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY r.createdAt DESC
            """)
    List<ReturnRequest> searchVisible(
            @Param("branchId") Long branchId,
            @Param("status") ReturnRequestStatus status,
            @Param("search") String search
    );

    @Query("""
            SELECT DISTINCT r
            FROM ReturnRequest r
            LEFT JOIN FETCH r.order o
            LEFT JOIN FETCH r.branch b
            LEFT JOIN FETCH r.items items
            WHERE r.status = com.zone.agri.entity.enums.ReturnRequestStatus.REFUNDED
              AND (:branchId IS NULL OR b.id = :branchId)
              AND (
                  (r.refundedAt IS NOT NULL AND r.refundedAt >= :startDateTime AND r.refundedAt <= :endDateTime)
                  OR (r.refundedAt IS NULL AND r.createdAt >= :startDateTime AND r.createdAt <= :endDateTime)
              )
            ORDER BY r.refundedAt DESC, r.createdAt DESC
            """)
    List<ReturnRequest> findReportData(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("branchId") Long branchId
    );
}

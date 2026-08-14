package com.zone.agri.repository;

import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface SubOrderRepository extends JpaRepository<SubOrder, Long> {

    interface FinancialSubOrderProjection {
        Long getSubOrderId();

        Long getOrderId();

        String getOrderCode();

        java.math.BigDecimal getSubtotal();

        java.math.BigDecimal getShippingFee();

        java.math.BigDecimal getOrderSubtotal();

        java.math.BigDecimal getOrderDiscountAmount();

        java.time.LocalDateTime getReceivedAt();

        java.time.LocalDateTime getCompletedAt();

        java.time.LocalDateTime getReturnedAt();
    }

    List<SubOrder> findByOrderId(Long orderId);

    List<SubOrder> findByStatus(OrderStatus status);

    List<SubOrder> findByStatusAndUpdatedAtBefore(OrderStatus status, java.time.LocalDateTime updatedAt);

    @Query("SELECT s FROM SubOrder s LEFT JOIN FETCH s.order o LEFT JOIN FETCH o.user " +
            "WHERE s.branch.id = :branchId ORDER BY s.createdAt DESC")
    List<SubOrder> findByBranchIdOrderByCreatedAtDesc(@Param("branchId") Long branchId);

    @Query("SELECT s FROM SubOrder s WHERE s.branch.id = :branchId AND s.status = :status ORDER BY s.createdAt DESC")
    List<SubOrder> findByBranchIdAndStatusOrderByCreatedAtDesc(
            @Param("branchId") Long branchId,
            @Param("status") OrderStatus status);

    @Query("SELECT s FROM SubOrder s WHERE s.order.id = :orderId AND s.branch.id = :branchId")
    Optional<SubOrder> findByOrderIdAndBranchId(
            @Param("orderId") Long orderId,
            @Param("branchId") Long branchId);

    @Query("""
            SELECT DISTINCT s
            FROM SubOrder s
            LEFT JOIN FETCH s.order o
            LEFT JOIN FETCH s.branch b
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.productVariant pv
            WHERE s.id = :id
            """)
    Optional<SubOrder> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status <> com.zone.agri.entity.enums.OrderStatus.CANCELLED " +
            "AND (:branchId IS NULL OR s.branch.id = :branchId)")
    long countAllByBranchIdExceptCancelled(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status <> com.zone.agri.entity.enums.OrderStatus.CANCELLED " +
            "AND s.createdAt <= :endDate AND (:branchId IS NULL OR s.branch.id = :branchId)")
    long countAllByBranchIdExceptCancelledBefore(@Param("endDate") java.time.LocalDateTime endDate,
                                                 @Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
            "AND s.createdAt BETWEEN :startDate AND :endDate " +
            "AND (:branchId IS NULL OR s.branch.id = :branchId)")
    long countSuccessByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                @Param("endDate") java.time.LocalDateTime endDate,
                                @Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status IN (com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.COMPLETED) " +
            "AND s.receivedAt BETWEEN :startDate AND :endDate " +
            "AND (:branchId IS NULL OR s.branch.id = :branchId)")
    long countDeliveredByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                  @Param("endDate") java.time.LocalDateTime endDate,
                                  @Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status = com.zone.agri.entity.enums.OrderStatus.RETURNED " +
            "AND s.returnedAt BETWEEN :startDate AND :endDate " +
            "AND (:branchId IS NULL OR s.branch.id = :branchId)")
    long countReturnedByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                 @Param("endDate") java.time.LocalDateTime endDate,
                                 @Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status = com.zone.agri.entity.enums.OrderStatus.CANCELLED " +
            "AND s.cancelledAt BETWEEN :startDate AND :endDate " +
            "AND (:branchId IS NULL OR s.branch.id = :branchId)")
    long countCancelledByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                  @Param("endDate") java.time.LocalDateTime endDate,
                                  @Param("branchId") Long branchId);

    @Query("SELECT SUM(s.subtotal + s.shippingFee) FROM SubOrder s WHERE s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
            "AND s.createdAt BETWEEN :startDate AND :endDate " +
            "AND (:branchId IS NULL OR s.branch.id = :branchId)")
    java.math.BigDecimal sumRevenueByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                              @Param("endDate") java.time.LocalDateTime endDate,
                                              @Param("branchId") Long branchId);

    interface DashboardRevenueRow {
        java.time.LocalDateTime getCreatedAt();

        java.math.BigDecimal getSubtotal();

        java.math.BigDecimal getShippingFee();

        java.math.BigDecimal getOrderSubtotal();

        java.math.BigDecimal getOrderDiscountAmount();
    }

    @Query("""
            SELECT s.createdAt AS createdAt,
                   COALESCE(s.subtotal, 0) AS subtotal,
                   COALESCE(s.shippingFee, 0) AS shippingFee,
                   COALESCE(o.totalAmount, 0) AS orderSubtotal,
                   COALESCE(o.discountAmount, 0) AS orderDiscountAmount
            FROM SubOrder s
            JOIN s.order o
            WHERE s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING)
              AND s.createdAt BETWEEN :startDate AND :endDate
              AND (:branchId IS NULL OR s.branch.id = :branchId)
            """)
    List<DashboardRevenueRow> findRevenueRows(@Param("startDate") java.time.LocalDateTime startDate,
                                              @Param("endDate") java.time.LocalDateTime endDate,
                                              @Param("branchId") Long branchId);

    @Query("SELECT CAST(s.createdAt AS date) as date, SUM(s.subtotal + s.shippingFee) as revenue, COUNT(s) as orderCount " +
            "FROM SubOrder s WHERE s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
            "AND s.createdAt BETWEEN :startDate AND :endDate " +
            "AND s.branch.id = :branchId " +
            "GROUP BY CAST(s.createdAt AS date) " +
            "ORDER BY CAST(s.createdAt AS date) ASC")
    List<Object[]> getDailyStatsByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                           @Param("endDate") java.time.LocalDateTime endDate,
                                           @Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status = :status AND (:branchId IS NULL OR s.branch.id = :branchId)")
    long countByStatusAndBranchId(@Param("status") OrderStatus status, @Param("branchId") Long branchId);

    @Query("SELECT s FROM SubOrder s WHERE s.status = :status AND s.branch.id = :branchId ORDER BY s.createdAt DESC")
    List<SubOrder> findPendingByBranchId(@Param("status") OrderStatus status, @Param("branchId") Long branchId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT CAST(s.createdAt AS date) as date, SUM(ABS(it.quantityChange) * i.importPrice) as cost " +
            "FROM InventoryTransaction it " +
            "JOIN it.inventory i " +
            "JOIN Order o ON (it.referenceCode = o.code OR it.referenceCode LIKE CONCAT(o.code, '-SUB-%')) " +
            "JOIN SubOrder s ON s.order.id = o.id AND s.branch.id = i.branch.id " +
            "WHERE it.type = com.zone.agri.entity.enums.TransactionType.SALE " +
            "AND i.branch.id = :branchId " +
            "AND s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
            "AND s.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY CAST(s.createdAt AS date)")
    List<Object[]> getDailyCostsByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                           @Param("endDate") java.time.LocalDateTime endDate,
                                           @Param("branchId") Long branchId);

    @Query("SELECT c.id AS categoryId, c.name AS categoryName, " +
            "SUM(si.unitPrice * si.quantity) AS totalRevenue, " +
            "SUM(si.quantity) AS totalQuantity " +
            "FROM SubOrderItem si " +
            "JOIN si.subOrder s " +
            "JOIN si.productVariant pv " +
            "JOIN pv.product p " +
            "JOIN p.category c " +
            "WHERE (:branchId IS NULL OR s.branch.id = :branchId) " +
            "AND s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
            "GROUP BY c.id, c.name " +
            "ORDER BY totalRevenue DESC")
    List<ProductRepository.CategorySalesProjection> getCategorySalesByBranch(@Param("branchId") Long branchId);

    @Query("""
            SELECT DISTINCT s
            FROM SubOrder s
            LEFT JOIN FETCH s.order o
            LEFT JOIN FETCH o.user u
            LEFT JOIN FETCH s.branch b
            LEFT JOIN FETCH s.items i
            LEFT JOIN FETCH i.productVariant pv
            LEFT JOIN FETCH pv.product p
            WHERE s.createdAt BETWEEN :startDate AND :endDate
              AND (:branchId IS NULL OR b.id = :branchId)
            ORDER BY s.createdAt DESC
            """)
    List<SubOrder> findReportData(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate,
                                  @Param("branchId") Long branchId);

    @Query("""
            SELECT s.id AS subOrderId,
                   o.id AS orderId,
                   o.code AS orderCode,
                   COALESCE(s.subtotal, 0) AS subtotal,
                   COALESCE(s.shippingFee, 0) AS shippingFee,
                   COALESCE(o.totalAmount, 0) AS orderSubtotal,
                   COALESCE(o.discountAmount, 0) AS orderDiscountAmount,
                   s.receivedAt AS receivedAt,
                   s.completedAt AS completedAt,
                   s.returnedAt AS returnedAt
            FROM SubOrder s
            JOIN s.order o
            WHERE (:branchId IS NULL OR s.branch.id = :branchId)
              AND (
                   (
                       CASE
                           WHEN s.receivedAt IS NULL THEN s.completedAt
                           WHEN s.completedAt IS NULL THEN s.receivedAt
                           WHEN s.receivedAt <= s.completedAt THEN s.receivedAt
                           ELSE s.completedAt
                       END
                   ) BETWEEN :startDate AND :endDate
                   OR (s.returnedAt IS NOT NULL AND s.returnedAt BETWEEN :startDate AND :endDate)
              )
            """)
    List<FinancialSubOrderProjection> findFinancialSubOrders(@Param("startDate") LocalDateTime startDate,
                                                             @Param("endDate") LocalDateTime endDate,
                                                             @Param("branchId") Long branchId);

    interface SubOrderAmountProjection {
        java.math.BigDecimal getSubtotal();
        java.math.BigDecimal getShippingFee();
        java.math.BigDecimal getOrderSubtotal();
        java.math.BigDecimal getOrderDiscountAmount();
    }

    @Query("""
        SELECT COALESCE(s.subtotal, 0) AS subtotal,
               COALESCE(s.shippingFee, 0) AS shippingFee,
               COALESCE(o.totalAmount, 0) AS orderSubtotal,
               COALESCE(o.discountAmount, 0) AS orderDiscountAmount
        FROM SubOrder s
        JOIN s.order o
        WHERE o.paymentStatus = com.zone.agri.entity.enums.PaymentStatus.PAID
          AND o.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND s.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND (:branchId IS NULL OR s.branch.id = :branchId)
          AND (
            CASE WHEN o.paymentMethod = com.zone.agri.entity.enums.PaymentMethod.COD
                 THEN COALESCE(s.receivedAt, s.completedAt, s.createdAt)
                 ELSE o.createdAt
            END < :startDate
          )
    """)
    List<SubOrderAmountProjection> findPaidSubOrderAmountsBefore(
            @Param("startDate") LocalDateTime startDate,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT COALESCE(s.subtotal, 0) AS subtotal,
               COALESCE(s.shippingFee, 0) AS shippingFee,
               COALESCE(o.totalAmount, 0) AS orderSubtotal,
               COALESCE(o.discountAmount, 0) AS orderDiscountAmount
        FROM SubOrder s
        JOIN s.order o
        WHERE o.paymentStatus = com.zone.agri.entity.enums.PaymentStatus.UNPAID
          AND o.paymentMethod = com.zone.agri.entity.enums.PaymentMethod.COD
          AND s.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND (:branchId IS NULL OR s.branch.id = :branchId)
    """)
    List<SubOrderAmountProjection> findUnpaidCodSubOrderAmounts(@Param("branchId") Long branchId);

    @Query("""
        SELECT s
        FROM SubOrder s
        JOIN FETCH s.order o
        LEFT JOIN FETCH o.user
        LEFT JOIN FETCH s.branch
        WHERE o.paymentStatus = com.zone.agri.entity.enums.PaymentStatus.PAID
          AND o.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND s.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND (:branchId IS NULL OR s.branch.id = :branchId)
          AND (
            CASE WHEN o.paymentMethod = com.zone.agri.entity.enums.PaymentMethod.COD
                 THEN COALESCE(s.receivedAt, s.completedAt, s.createdAt)
                 ELSE o.createdAt
            END BETWEEN :startDate AND :endDate
          )
    """)
    List<SubOrder> findPaidSubOrdersInRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT s
        FROM SubOrder s
        JOIN FETCH s.order o
        LEFT JOIN FETCH o.user
        LEFT JOIN FETCH s.branch
        WHERE o.paymentStatus = com.zone.agri.entity.enums.PaymentStatus.PAID
          AND o.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND s.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND (:branchId IS NULL OR s.branch.id = :branchId)
          AND (
            CASE WHEN o.paymentMethod = com.zone.agri.entity.enums.PaymentMethod.COD
                 THEN COALESCE(s.receivedAt, s.completedAt, s.createdAt)
                 ELSE o.createdAt
            END <= :endDate
          )
    """)
    List<SubOrder> findPaidSubOrdersBefore(
            @Param("endDate") LocalDateTime endDate,
            @Param("branchId") Long branchId);
}


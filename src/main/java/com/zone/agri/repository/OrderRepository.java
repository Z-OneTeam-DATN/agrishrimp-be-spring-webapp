package com.zone.agri.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.enums.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

       interface LegacyFinancialOrderProjection {
              Long getId();

              String getOrderCode();

              BigDecimal getProductAmount();

              BigDecimal getShippingAmount();

              BigDecimal getDiscountAmount();

              LocalDateTime getReceivedAt();

              LocalDateTime getCompletedAt();

              LocalDateTime getReturnedAt();
       }

       java.util.Optional<Order> findByCode(String code);

       boolean existsByStatusIn(Collection<OrderStatus> statuses);

       @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.orderItems item WHERE o.status IN :statuses AND item.productVariant.product.id = :productId")
       boolean existsByStatusInAndProductId(@Param("statuses") Collection<OrderStatus> statuses,
                     @Param("productId") Long productId);

       @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
       Long countTotalOrdersByUserId(@Param("userId") Long userId);

       @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)")
       Long countSettledOrdersByUserId(@Param("userId") Long userId);

       @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.status = 'COMPLETED'")
       Long countCompletedOrdersByUserId(@Param("userId") Long userId);

       @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.user.id = :userId AND o.status = 'COMPLETED'")
       BigDecimal sumTotalSpentByUserId(@Param("userId") Long userId);

       @Query("SELECT MAX(o.createdAt) FROM Order o WHERE o.user.id = :userId")
       LocalDateTime findLastOrderDateByUserId(@Param("userId") Long userId);

       @Query("SELECT AVG(o.finalAmount) FROM Order o WHERE o.user.id = :userId AND o.status = 'COMPLETED'")
       Double findAverageOrderValueByUserId(@Param("userId") Long userId);

       @Query("SELECT COUNT(o) > 0 FROM Order o " +
                     "WHERE o.id = :orderId AND o.user.id = :userId " +
                     "AND (" +
                     "  (o.status = 'COMPLETED' AND EXISTS (SELECT 1 FROM o.orderItems oi WHERE oi.productVariant.product.id = :productId)) "
                     +
                     "  OR EXISTS (SELECT 1 FROM o.subOrders so JOIN so.items si WHERE so.status = 'COMPLETED' AND si.productVariant.product.id = :productId)"
                     +
                     ")")
       boolean existsCompletedOrderWithProduct(@Param("orderId") Long orderId, @Param("userId") Long userId,
                     @Param("productId") Long productId);

       List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

       List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAt);

       List<Order> findByStatusAndUpdatedAtBefore(OrderStatus status, LocalDateTime updatedAt);

       @Query("""
              SELECT o
              FROM Order o
              WHERE o.status = com.zone.agri.entity.enums.OrderStatus.PENDING
                AND o.autoApproveAt IS NOT NULL
                AND o.autoApproveAt <= :now
                AND (o.autoApprovalPaused IS NULL OR o.autoApprovalPaused = false)
              ORDER BY o.autoApproveAt ASC
              """)
       List<Order> findOrdersReadyForAutoApproval(@Param("now") LocalDateTime now);

       List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

       List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

       @Query("""
                     SELECT o
                     FROM Order o
                     WHERE o.paymentMethod = com.zone.agri.entity.enums.PaymentMethod.PAYOS
                       AND o.paymentStatus IN (
                            com.zone.agri.entity.enums.PaymentStatus.UNPAID,
                            com.zone.agri.entity.enums.PaymentStatus.PENDING
                       )
                       AND o.status NOT IN (
                            com.zone.agri.entity.enums.OrderStatus.CANCELLED,
                            com.zone.agri.entity.enums.OrderStatus.RETURNED,
                            com.zone.agri.entity.enums.OrderStatus.COMPLETED
                       )
                     ORDER BY o.createdAt ASC
                     """)
       Page<Order> findPendingPayosOrdersForReconcile(Pageable pageable);

       @Query("SELECT o FROM Order o WHERE o.status = :status " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId) " +
                     "ORDER BY o.createdAt DESC")
       List<Order> findPendingOrders(@Param("status") OrderStatus status,
                     @Param("branchId") Long branchId,
                     Pageable pageable);

       @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       long countByStatus(@Param("status") OrderStatus status, @Param("branchId") Long branchId);

       @Query("SELECT o FROM Order o WHERE (:branchId IS NULL OR o.branch.id = :branchId) ORDER BY o.createdAt DESC")
       List<Order> findRecentOrders(@Param("branchId") Long branchId, Pageable pageable);

       List<Order> findAllByOrderByCreatedAtDesc();

       @Query("SELECT COUNT(o) FROM Order o WHERE o.status <> com.zone.agri.entity.enums.OrderStatus.CANCELLED " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       long countAllOrdersExceptCancelled(@Param("branchId") Long branchId);

       @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       long countSuccessOrders(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumGrossRevenue(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT SUM(o.finalAmount) FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumRecognizedFinalAmount(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       default BigDecimal sumTotalRevenue(LocalDateTime startDate, LocalDateTime endDate, Long branchId) {
              return sumRecognizedFinalAmount(startDate, endDate, branchId);
       }

       @Query("SELECT SUM(ABS(it.quantityChange) * i.importPrice) " +
                     "FROM Order o " +
                     "JOIN InventoryTransaction it ON it.referenceCode = o.code " +
                     "JOIN it.inventory i " +
                     "WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND it.quantityChange < 0 " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR i.branch.id = :branchId)")
       BigDecimal sumTotalCost(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT CAST(o.createdAt AS date) as date, SUM(o.finalAmount) as revenue, COUNT(o) as orderCount " +
                     "FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId) " +
                     "GROUP BY CAST(o.createdAt AS date) " +
                     "ORDER BY CAST(o.createdAt AS date) ASC")
       List<Object[]> getDailyStats(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT CAST(o.createdAt AS date) as date, SUM(ABS(it.quantityChange) * i.importPrice) as cost " +
                     "FROM InventoryTransaction it " +
                     "JOIN it.inventory i " +
                     "JOIN Order o ON it.referenceCode = o.code " +
                     "WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND it.quantityChange < 0 " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR i.branch.id = :branchId) " +
                     "GROUP BY CAST(o.createdAt AS date)")
       List<Object[]> getDailyCosts(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'RETURNED' " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumReturnedGoods(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT SUM(o.totalShippingFee) FROM Order o WHERE o.status = 'RETURNED' " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumReturnedShippingFee(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT SUM(o.discountAmount) FROM Order o WHERE o.status = 'RETURNED' " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumReturnedDiscount(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT SUM(o.totalShippingFee) FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumShippingFee(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT SUM(o.discountAmount) FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.RECEIVED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumDiscount(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("""
                     SELECT o.id AS id,
                            o.code AS orderCode,
                            COALESCE(o.totalAmount, 0) AS productAmount,
                            COALESCE(o.totalShippingFee, 0) AS shippingAmount,
                            COALESCE(o.discountAmount, 0) AS discountAmount,
                            o.receivedAt AS receivedAt,
                            o.completedAt AS completedAt,
                            o.returnedAt AS returnedAt
                     FROM Order o
                     WHERE o.subOrders IS EMPTY
                       AND (:branchId IS NULL OR o.branch.id = :branchId)
                       AND (
                            (
                                CASE
                                    WHEN o.receivedAt IS NULL THEN o.completedAt
                                    WHEN o.completedAt IS NULL THEN o.receivedAt
                                    WHEN o.receivedAt <= o.completedAt THEN o.receivedAt
                                    ELSE o.completedAt
                                END
                            ) BETWEEN :startDate AND :endDate
                            OR (o.returnedAt IS NOT NULL AND o.returnedAt BETWEEN :startDate AND :endDate)
                       )
                     """)
       List<LegacyFinancialOrderProjection> findLegacyFinancialOrders(
                      @Param("startDate") LocalDateTime startDate,
                      @Param("endDate") LocalDateTime endDate,
                      @Param("branchId") Long branchId);

       @Query("SELECT COALESCE(SUM(o.finalAmount), 0) FROM Order o " +
              "WHERE o.paymentStatus = com.zone.agri.entity.enums.PaymentStatus.UNPAID " +
              "AND o.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED) " +
              "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumUnpaidOrdersAmount(@Param("branchId") Long branchId);

    @Query("""
        SELECT COALESCE(SUM(o.finalAmount), 0)
        FROM Order o
        WHERE o.paymentStatus = com.zone.agri.entity.enums.PaymentStatus.PAID
          AND o.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND (:branchId IS NULL OR o.branch.id = :branchId)
          AND o.subOrders IS EMPTY
          AND (
            CASE WHEN o.paymentMethod = com.zone.agri.entity.enums.PaymentMethod.COD
                 THEN COALESCE(o.receivedAt, o.completedAt, o.createdAt)
                 ELSE o.createdAt
            END < :startDate
          )
    """)
    BigDecimal sumPaidLegacyOrdersAmountBefore(
            @Param("startDate") LocalDateTime startDate,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT o
        FROM Order o
        WHERE o.paymentStatus = com.zone.agri.entity.enums.PaymentStatus.PAID
          AND o.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND (:branchId IS NULL OR o.branch.id = :branchId)
          AND o.subOrders IS EMPTY
          AND (
            CASE WHEN o.paymentMethod = com.zone.agri.entity.enums.PaymentMethod.COD
                 THEN COALESCE(o.receivedAt, o.completedAt, o.createdAt)
                 ELSE o.createdAt
            END BETWEEN :startDate AND :endDate
          )
    """)
    List<Order> findPaidLegacyOrdersInRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT o
        FROM Order o
        WHERE o.paymentStatus = com.zone.agri.entity.enums.PaymentStatus.PAID
          AND o.status NOT IN (com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)
          AND (:branchId IS NULL OR o.branch.id = :branchId)
          AND o.subOrders IS EMPTY
          AND (
            CASE WHEN o.paymentMethod = com.zone.agri.entity.enums.PaymentMethod.COD
                 THEN COALESCE(o.receivedAt, o.completedAt, o.createdAt)
                 ELSE o.createdAt
            END <= :endDate
          )
    """)
    List<Order> findPaidLegacyOrdersBefore(
            @Param("endDate") LocalDateTime endDate,
            @Param("branchId") Long branchId);
}

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

    List<SubOrder> findByOrderId(Long orderId);

    // ── Truy vấn theo chi nhánh (dùng cho quản lý kho / chi nhánh) ──

    @Query("SELECT s FROM SubOrder s WHERE s.branch.id = :branchId ORDER BY s.createdAt DESC")
    List<SubOrder> findByBranchIdOrderByCreatedAtDesc(@Param("branchId") Long branchId);

    @Query("SELECT s FROM SubOrder s WHERE s.branch.id = :branchId AND s.status = :status ORDER BY s.createdAt DESC")
    List<SubOrder> findByBranchIdAndStatusOrderByCreatedAtDesc(
            @Param("branchId") Long branchId,
            @Param("status") OrderStatus status);

    @Query("SELECT s FROM SubOrder s WHERE s.order.id = :orderId AND s.branch.id = :branchId")
    Optional<SubOrder> findByOrderIdAndBranchId(
            @Param("orderId") Long orderId,
            @Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status <> com.zone.agri.entity.enums.OrderStatus.CANCELLED " +
            "AND s.branch.id = :branchId")
    long countAllByBranchIdExceptCancelled(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
            "AND s.createdAt BETWEEN :startDate AND :endDate " +
            "AND s.branch.id = :branchId")
    long countSuccessByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                @Param("endDate") java.time.LocalDateTime endDate,
                                @Param("branchId") Long branchId);

    @Query("SELECT SUM(s.subtotal + s.shippingFee) FROM SubOrder s WHERE s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
            "AND s.createdAt BETWEEN :startDate AND :endDate " +
            "AND s.branch.id = :branchId")
    java.math.BigDecimal sumRevenueByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                              @Param("endDate") java.time.LocalDateTime endDate,
                                              @Param("branchId") Long branchId);

    @Query("SELECT CAST(s.createdAt AS date) as date, SUM(s.subtotal + s.shippingFee) as revenue, COUNT(s) as orderCount " +
            "FROM SubOrder s WHERE s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
            "AND s.createdAt BETWEEN :startDate AND :endDate " +
            "AND s.branch.id = :branchId " +
            "GROUP BY CAST(s.createdAt AS date) " +
            "ORDER BY CAST(s.createdAt AS date) ASC")
    List<Object[]> getDailyStatsByBranchId(@Param("startDate") java.time.LocalDateTime startDate,
                                           @Param("endDate") java.time.LocalDateTime endDate,
                                           @Param("branchId") Long branchId);

    @Query("SELECT COUNT(s) FROM SubOrder s WHERE s.status = :status AND s.branch.id = :branchId")
    long countByStatusAndBranchId(@Param("status") OrderStatus status, @Param("branchId") Long branchId);

    @Query("SELECT s FROM SubOrder s WHERE s.status = :status AND s.branch.id = :branchId ORDER BY s.createdAt DESC")
    List<SubOrder> findPendingByBranchId(@Param("status") OrderStatus status, @Param("branchId") Long branchId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT CAST(s.createdAt AS date) as date, SUM(ABS(it.quantityChange) * i.importPrice) as cost " +
            "FROM InventoryTransaction it " +
            "JOIN it.inventory i " +
            "JOIN Order o ON it.referenceCode = o.code " +
            "JOIN SubOrder s ON s.order.id = o.id AND s.branch.id = i.branch.id " +
            "WHERE it.type = com.zone.agri.entity.enums.TransactionType.SALE " +
            "AND i.branch.id = :branchId " +
            "AND s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
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
            "WHERE s.branch.id = :branchId " +
            "AND s.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) " +
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
}

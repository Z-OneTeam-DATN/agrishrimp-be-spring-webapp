package com.zone.agri.repository;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

       java.util.Optional<Order> findByCode(String code);

       boolean existsByStatusIn(Collection<OrderStatus> statuses);

       @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.orderItems item WHERE o.status IN :statuses AND item.productVariant.product.id = :productId")
       boolean existsByStatusInAndProductId(@Param("statuses") Collection<OrderStatus> statuses,
                     @Param("productId") Long productId);

       // 1. Đếm tổng số đơn hàng của khách
       @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
       Long countTotalOrdersByUserId(@Param("userId") Long userId);

       // 1b. Đếm số đơn đã ngã ngũ, dùng để tính uy tín
       @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.CANCELLED, com.zone.agri.entity.enums.OrderStatus.RETURNED)")
       Long countSettledOrdersByUserId(@Param("userId") Long userId);

       // 2. Đếm số đơn hàng giao THÀNH CÔNG
       @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.status = 'COMPLETED'")
       Long countCompletedOrdersByUserId(@Param("userId") Long userId);

       // 3. Tính tổng tiền khách đã chi (Chỉ cộng đơn THÀNH CÔNG)
       @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.user.id = :userId AND o.status = 'COMPLETED'")
       BigDecimal sumTotalSpentByUserId(@Param("userId") Long userId);

       @Query("SELECT MAX(o.createdAt) FROM Order o WHERE o.user.id = :userId")
       LocalDateTime findLastOrderDateByUserId(@Param("userId") Long userId);

       @Query("SELECT AVG(o.finalAmount) FROM Order o WHERE o.user.id = :userId AND o.status = 'COMPLETED'")
       Double findAverageOrderValueByUserId(@Param("userId") Long userId);

       // Thêm hàm này để lấy lịch sử đơn hàng của 1 khách hàng cụ thể
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

       List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

       List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

       @Query("SELECT o FROM Order o WHERE " +
                     "(:status IS NULL OR o.status = :status) AND " +
                     "(:search IS NULL OR LOWER(o.code) LIKE LOWER(CONCAT('%', LOWER(:search), '%')) OR LOWER(o.user.fullName) LIKE LOWER(CONCAT('%', LOWER(:search), '%'))) "
                     +
                     "ORDER BY o.createdAt DESC")
       Page<Order> findAdminOrdersWithFilter(@Param("status") OrderStatus status, @Param("search") String search,
                     Pageable pageable);

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

       @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       long countSuccessOrders(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT SUM(o.finalAmount) FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumTotalRevenue(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       // Tính tổng giá vốn (COGS) cho cả COMPLETED và SHIPPING
       @Query("SELECT SUM(ABS(it.quantityChange) * i.importPrice) " +
                     "FROM Order o " +
                     "JOIN InventoryTransaction it ON it.referenceCode = o.code " +
                     "JOIN it.inventory i " +
                     "WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND it.quantityChange < 0 " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR i.branch.id = :branchId)")
       BigDecimal sumTotalCost(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       @Query("SELECT CAST(o.createdAt AS date) as date, SUM(o.finalAmount) as revenue, COUNT(o) as orderCount " +
                     "FROM Order o WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
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
                     "WHERE o.status IN (com.zone.agri.entity.enums.OrderStatus.COMPLETED, com.zone.agri.entity.enums.OrderStatus.SHIPPING) "
                     +
                     "AND it.quantityChange < 0 " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR i.branch.id = :branchId) " +
                     "GROUP BY CAST(o.createdAt AS date)")
       List<Object[]> getDailyCosts(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       // Tính tổng tiền hàng bị trả lại (Đơn hàng bị RETURNED)
       @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'RETURNED' " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumReturnedGoods(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       // Tính tổng phí ship thu của khách
       @Query("SELECT SUM(o.totalShippingFee) FROM Order o WHERE o.status = 'COMPLETED' " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumShippingFee(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);

       // Tính tổng chiết khấu cho khách
       @Query("SELECT SUM(o.discountAmount) FROM Order o WHERE o.status = 'COMPLETED' " +
                     "AND o.createdAt BETWEEN :startDate AND :endDate " +
                     "AND (:branchId IS NULL OR o.branch.id = :branchId)")
       BigDecimal sumDiscount(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     @Param("branchId") Long branchId);
}

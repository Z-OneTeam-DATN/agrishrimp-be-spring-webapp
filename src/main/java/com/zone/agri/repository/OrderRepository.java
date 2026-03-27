package com.zone.agri.repository;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.enums.OrderStatus;
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
    boolean existsByStatusInAndProductId(@Param("statuses") Collection<OrderStatus> statuses, @Param("productId") Long productId);

    // 1. Đếm tổng số đơn hàng của khách
    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    Long countTotalOrdersByUserId(@Param("userId") Long userId);

    // 2. Đếm số đơn hàng giao THÀNH CÔNG
    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.status = 'COMPLETED'")
    Long countCompletedOrdersByUserId(@Param("userId") Long userId);

    // 3. Tính tổng tiền khách đã chi (Chỉ cộng đơn THÀNH CÔNG)
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.user.id = :userId AND o.status = 'COMPLETED'")
    BigDecimal sumTotalSpentByUserId(@Param("userId") Long userId);

    // Thêm hàm này để lấy lịch sử đơn hàng của 1 khách hàng cụ thể
    @Query("SELECT COUNT(o) > 0 FROM Order o " +
            "WHERE o.id = :orderId AND o.user.id = :userId " +
            "AND (" +
            "  (o.status = 'COMPLETED' AND EXISTS (SELECT 1 FROM o.orderItems oi WHERE oi.productVariant.product.id = :productId)) " +
            "  OR EXISTS (SELECT 1 FROM o.subOrders so JOIN so.items si WHERE so.status = 'COMPLETED' AND si.productVariant.product.id = :productId)" +
            ")")
    boolean existsCompletedOrderWithProduct(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("productId") Long productId);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<Order> findAllByOrderByCreatedAtDesc();

    // Tính tổng doanh thu (Đơn hàng đã hoàn thành)
    @Query("SELECT SUM(o.finalAmount) FROM Order o WHERE o.status = 'COMPLETED' " +
            "AND o.createdAt BETWEEN :startDate AND :endDate " +
            "AND (:branchId IS NULL OR o.branch.id = :branchId)")
    BigDecimal sumRevenue(@Param("startDate") LocalDateTime startDate,
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

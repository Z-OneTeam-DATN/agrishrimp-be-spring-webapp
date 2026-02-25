package com.zone.agri.repository;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

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
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}

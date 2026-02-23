package com.zone.agri.repository;

import com.zone.agri.entity.Order;
import com.zone.agri.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    boolean existsByStatusIn(Collection<OrderStatus> statuses);

    @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.orderItems item WHERE o.status IN :statuses AND item.productVariant.product.id = :productId")
    boolean existsByStatusInAndProductId(@Param("statuses") Collection<OrderStatus> statuses, @Param("productId") Long productId);
}

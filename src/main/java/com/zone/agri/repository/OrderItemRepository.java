package com.zone.agri.repository;

import com.zone.agri.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByProductVariantProductId(Long productId);
}

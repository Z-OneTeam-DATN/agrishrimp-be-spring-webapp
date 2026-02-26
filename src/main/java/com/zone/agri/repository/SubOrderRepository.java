package com.zone.agri.repository;

import com.zone.agri.entity.SubOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubOrderRepository extends JpaRepository<SubOrder, Long> {
    List<SubOrder> findByOrderId(Long orderId);
}

package com.zone.agri.repository;

import com.zone.agri.entity.SubOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubOrderItemRepository extends JpaRepository<SubOrderItem, Long> {
    List<SubOrderItem> findBySubOrderId(Long subOrderId);
}

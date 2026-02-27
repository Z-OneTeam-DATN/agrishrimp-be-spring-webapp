package com.zone.agri.repository;

import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
}

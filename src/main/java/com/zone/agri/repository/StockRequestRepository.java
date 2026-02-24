package com.zone.agri.repository;

import com.zone.agri.entity.StockRequest;
import com.zone.agri.entity.enums.StockRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockRequestRepository extends JpaRepository<StockRequest, Long> {

    List<StockRequest> findByToBranchIdOrderByCreatedAtDesc(Long toBranchId);

    List<StockRequest> findAllByOrderByCreatedAtDesc();

    List<StockRequest> findByToBranchIdAndStatusOrderByCreatedAtDesc(Long toBranchId, StockRequestStatus status);

    @Query("SELECT CASE WHEN COUNT(sr) > 0 THEN true ELSE false END " +
           "FROM StockRequest sr JOIN sr.items i " +
           "WHERE i.productVariant.id = :variantId " +
           "AND sr.status = 'PENDING'")
    boolean existsPendingByVariantId(@Param("variantId") Long variantId);

    boolean existsByRequestCode(String requestCode);
}

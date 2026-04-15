package com.zone.agri.repository;

import com.zone.agri.entity.PurchaseRequestItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, Long> {

    List<PurchaseRequestItem> findByPurchaseRequestId(Long purchaseRequestId);

    // Dùng pessimistic lock để tránh race condition khi nhiều phiếu nhập hoàn tất đồng thời
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT i FROM PurchaseRequestItem i
        WHERE i.purchaseRequest.id = :prId
          AND i.productVariant.id = :variantId
    """)
    Optional<PurchaseRequestItem> findByPrIdAndVariantWithLock(
            @Param("prId") Long prId,
            @Param("variantId") Long variantId
    );

    @Query("""
        SELECT i
        FROM PurchaseRequestItem i
        LEFT JOIN FETCH i.productVariant pv
        LEFT JOIN FETCH pv.product
        WHERE i.purchaseRequest.id = :prId
    """)
    List<PurchaseRequestItem> findByPurchaseRequestIdWithVariants(@Param("prId") Long prId);

    // Kiểm tra tất cả items đã đủ acceptedQty chưa (remainingQty == 0)
    @Query("""
        SELECT COUNT(i) FROM PurchaseRequestItem i
        WHERE i.purchaseRequest.id = :prId
          AND i.remainingQty > 0
    """)
    long countItemsWithRemainingQty(@Param("prId") Long prId);
}

package com.zone.agri.repository;

import com.zone.agri.entity.SubOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubOrderItemRepository extends JpaRepository<SubOrderItem, Long> {
    List<SubOrderItem> findBySubOrderId(Long subOrderId);

    @Query("""
            SELECT soi
            FROM SubOrderItem soi
            JOIN FETCH soi.subOrder so
            JOIN FETCH so.order o
            JOIN FETCH soi.productVariant pv
            WHERE so.branch.id = :branchId
              AND so.status NOT IN (
                  com.zone.agri.entity.enums.OrderStatus.CANCELLED,
                  com.zone.agri.entity.enums.OrderStatus.RETURNED,
                  com.zone.agri.entity.enums.OrderStatus.COMPLETED
              )
              AND pv.id = :productVariantId
              AND COALESCE(soi.missingQuantity, 0) > 0
            ORDER BY so.createdAt ASC, soi.id ASC
            """)
    List<SubOrderItem> findBackorderItemsForFulfillment(
            @Param("branchId") Long branchId,
            @Param("productVariantId") Long productVariantId
    );

    @Query("""
            SELECT pv.id AS productVariantId,
                   pv.sku AS sku,
                   p.name AS productName,
                   pv.customSpecs AS variantName,
                   SUM(COALESCE(soi.missingQuantity, 0)) AS totalMissingQuantity,
                   COUNT(DISTINCT so.id) AS affectedSubOrders
            FROM SubOrderItem soi
            JOIN soi.subOrder so
            JOIN soi.productVariant pv
            JOIN pv.product p
            WHERE so.status NOT IN (
                com.zone.agri.entity.enums.OrderStatus.CANCELLED,
                com.zone.agri.entity.enums.OrderStatus.RETURNED,
                com.zone.agri.entity.enums.OrderStatus.COMPLETED
            )
              AND COALESCE(soi.missingQuantity, 0) > 0
              AND (:branchId IS NULL OR so.branch.id = :branchId)
            GROUP BY pv.id, pv.sku, p.name, pv.customSpecs
            ORDER BY SUM(COALESCE(soi.missingQuantity, 0)) DESC, pv.id ASC
            """)
    List<BackorderReportProjection> getBackorderReport(@Param("branchId") Long branchId);

    interface BackorderReportProjection {
        Long getProductVariantId();
        String getSku();
        String getProductName();
        String getVariantName();
        Long getTotalMissingQuantity();
        Long getAffectedSubOrders();
    }
}

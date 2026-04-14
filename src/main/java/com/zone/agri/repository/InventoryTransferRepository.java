package com.zone.agri.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zone.agri.dto.response.transfer.TransferResponse;
import com.zone.agri.entity.InventoryTransfer;
import com.zone.agri.entity.enums.InventoryTransferStatus;

@Repository
public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long> {

        boolean existsByReferenceCodeAndStatusIn(String referenceCode, Collection<InventoryTransferStatus> statuses);

        // Kiểm tra sản phẩm có đang nằm trong các phiếu chuyển trạng thái nhất định
        // không
        @Query("SELECT COUNT(it) > 0 FROM InventoryTransfer it " +
                        "JOIN it.details detail " +
                        "WHERE it.status IN :statuses " +
                        "AND detail.productVariant.product.id = :productId")
        boolean existsByStatusInAndProductId(
                        @Param("statuses") Collection<InventoryTransferStatus> statuses,
                        @Param("productId") Long productId);

        // Search + phân trang
        @Query("SELECT new com.zone.agri.dto.response.transfer.TransferResponse(" +
                        "t.id, t.transferCode, t.status, t.createdAt, " +
                        "t.transferDate, t.deadline, " +
                        "fb.name, tb.name, t.transporter, t.priority, " +
                        "t.totalQuantity, SIZE(t.details), t.totalValue) " +
                        "FROM InventoryTransfer t " +
                        "LEFT JOIN t.fromBranch fb " +
                        "LEFT JOIN t.toBranch tb " +
                        "WHERE (:keyword IS NULL OR t.transferCode LIKE CONCAT('%', :keyword, '%') " +
                        "OR t.transporter LIKE CONCAT('%', :keyword, '%')) " +
                        "AND (:status IS NULL OR t.status = :status)")
        Page<TransferResponse> searchTransfers(
                        @Param("keyword") String keyword,
                        @Param("status") InventoryTransferStatus status,
                        Pageable pageable);

        // Đếm tổng số phiếu (generate mã)
        @Query("SELECT COUNT(t) FROM InventoryTransfer t")
        long countTotalTransfers();

        @Query("""
                        SELECT d.productVariant.sku,
                               COALESCE(SUM(COALESCE(d.quantityRequested, d.quantity)), 0)
                        FROM InventoryTransfer t
                        JOIN t.details d
                        WHERE t.toBranch.id = :toBranchId
                          AND t.status IN :statuses
                          AND d.productVariant.sku IN :skus
                        GROUP BY d.productVariant.sku
                        """)
        List<Object[]> sumInFlightQuantityByToBranchAndSkuIn(
                        @Param("toBranchId") Long toBranchId,
                        @Param("statuses") Collection<InventoryTransferStatus> statuses,
                        @Param("skus") Collection<String> skus);
}

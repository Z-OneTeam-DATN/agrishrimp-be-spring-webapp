package com.zone.agri.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
      "t.totalQuantity, SIZE(t.details), t.totalValue, " +
      "t.transferBusinessType, t.settlementStatus, t.transferAmount) " +
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

  @Query("SELECT new com.zone.agri.dto.response.transfer.TransferResponse(" +
      "t.id, t.transferCode, t.status, t.createdAt, " +
      "t.transferDate, t.deadline, " +
      "fb.name, tb.name, t.transporter, t.priority, " +
      "t.totalQuantity, SIZE(t.details), t.totalValue, " +
      "t.transferBusinessType, t.settlementStatus, t.transferAmount) " +
      "FROM InventoryTransfer t " +
      "LEFT JOIN t.fromBranch fb " +
      "LEFT JOIN t.toBranch tb " +
      "WHERE (:keyword IS NULL OR t.transferCode LIKE CONCAT('%', :keyword, '%') " +
      "OR t.transporter LIKE CONCAT('%', :keyword, '%')) " +
      "AND (:status IS NULL OR t.status = :status) " +
      "AND (fb.id = :branchId OR tb.id = :branchId)")
  Page<TransferResponse> searchTransfersForBranch(
      @Param("keyword") String keyword,
      @Param("status") InventoryTransferStatus status,
      @Param("branchId") Long branchId,
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

  /**
   * Tìm phiếu điều chuyển ORDER_REPLENISHMENT đang PENDING cho cùng tuyến
   * (fromBranch → toBranch) được tạo trong khoảng thời gian merge-open-hours.
   * Dùng để gộp hàng mới vào phiếu cũ thay vì tạo phiếu trùng.
   */
  @Query("SELECT t FROM InventoryTransfer t " +
      "WHERE t.fromBranch.id = :fromBranchId " +
      "AND t.toBranch.id = :toBranchId " +
      "AND t.transferType = 'ORDER_REPLENISHMENT' " +
      "AND t.status = com.zone.agri.entity.enums.InventoryTransferStatus.PENDING " +
      "AND t.createdAt >= :cutoffTime " +
      "ORDER BY t.createdAt DESC")
  Optional<InventoryTransfer> findLatestOpenReplenishmentTransfer(
      @Param("fromBranchId") Long fromBranchId,
      @Param("toBranchId") Long toBranchId,
      @Param("cutoffTime") LocalDateTime cutoffTime);

  // Tìm phiếu điều chuyển theo ID với eager load cho branches và details
  @Query("SELECT t FROM InventoryTransfer t " +
      "JOIN FETCH t.fromBranch " +
      "JOIN FETCH t.toBranch " +
      "LEFT JOIN FETCH t.details " +
      "WHERE t.id = :id")
  Optional<InventoryTransfer> findByIdWithDetails(@Param("id") Long id);
}

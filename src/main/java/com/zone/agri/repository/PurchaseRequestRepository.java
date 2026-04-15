package com.zone.agri.repository;

import com.zone.agri.entity.PurchaseRequest;
import com.zone.agri.entity.enums.PurchaseRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    @Query("""
        SELECT DISTINCT pr
        FROM PurchaseRequest pr
        LEFT JOIN FETCH pr.supplier
        LEFT JOIN FETCH pr.branch
        LEFT JOIN FETCH pr.createdBy
        ORDER BY pr.createdAt DESC
    """)
    List<PurchaseRequest> findAllWithRelations();

    @Query("""
        SELECT DISTINCT pr
        FROM PurchaseRequest pr
        LEFT JOIN FETCH pr.supplier
        LEFT JOIN FETCH pr.branch
        LEFT JOIN FETCH pr.createdBy
        WHERE pr.branch.id = :branchId
        ORDER BY pr.createdAt DESC
    """)
    List<PurchaseRequest> findAllByBranchWithRelations(@Param("branchId") Long branchId);

    @Query("""
        SELECT DISTINCT pr
        FROM PurchaseRequest pr
        LEFT JOIN FETCH pr.supplier
        LEFT JOIN FETCH pr.branch
        LEFT JOIN FETCH pr.createdBy
        LEFT JOIN FETCH pr.approvedBy
        LEFT JOIN FETCH pr.items i
        LEFT JOIN FETCH i.productVariant pv
        LEFT JOIN FETCH pv.product
        WHERE pr.id = :id
    """)
    Optional<PurchaseRequest> findByIdWithDetails(@Param("id") Long id);

    boolean existsByCode(String code);

    // Đếm phiếu nhập (GoodsReceipts) liên kết với yêu cầu này
    @Query("SELECT COUNT(n) FROM InventoryNote n WHERE n.purchaseRequest.id = :prId")
    long countGoodsReceiptsByPrId(@Param("prId") Long prId);

    // Tìm theo mã code
    Optional<PurchaseRequest> findByCode(String code);

    // Tìm các yêu cầu đang mở (cần nhận thêm hàng)
    @Query("""
        SELECT DISTINCT pr
        FROM PurchaseRequest pr
        LEFT JOIN FETCH pr.supplier
        LEFT JOIN FETCH pr.branch
        WHERE pr.status IN :statuses
        ORDER BY pr.createdAt DESC
    """)
    List<PurchaseRequest> findByStatusIn(@Param("statuses") List<PurchaseRequestStatus> statuses);
}

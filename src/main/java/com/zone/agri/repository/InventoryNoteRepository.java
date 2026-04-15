package com.zone.agri.repository;

import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

@Repository
public interface InventoryNoteRepository extends JpaRepository<InventoryNote, Long> {
    @Query("""
        SELECT DISTINCT inote 
        FROM InventoryNote inote 
        LEFT JOIN FETCH inote.branch 
        LEFT JOIN FETCH inote.partnerBranch 
        LEFT JOIN FETCH inote.supplier 
        LEFT JOIN FETCH inote.createdBy
        WHERE inote.type = :type
        ORDER BY inote.createdAt DESC
    """)
    List<InventoryNote> findAllByTypeWithPartners(@Param("type") InventoryNoteType type);

    @Query("""
        SELECT DISTINCT inote 
        FROM InventoryNote inote 
        LEFT JOIN FETCH inote.branch 
        LEFT JOIN FETCH inote.partnerBranch 
        LEFT JOIN FETCH inote.supplier 
        LEFT JOIN FETCH inote.createdBy
        WHERE inote.type = :type AND inote.branch.id = :branchId
        ORDER BY inote.createdAt DESC
    """)
    List<InventoryNote> findAllByTypeAndBranchWithPartners(
            @Param("type") InventoryNoteType type,
            @Param("branchId") Long branchId
    );

    @Query("""
        SELECT DISTINCT inote 
        FROM InventoryNote inote 
        LEFT JOIN FETCH inote.branch 
        LEFT JOIN FETCH inote.partnerBranch 
        LEFT JOIN FETCH inote.supplier 
        LEFT JOIN FETCH inote.createdBy
        WHERE inote.type = :type AND inote.status = :status
        ORDER BY inote.createdAt DESC
    """)
    List<InventoryNote> findAllByTypeAndStatusWithPartners(
            @Param("type") InventoryNoteType type,
            @Param("status") InventoryNoteStatus status
    );

    @Query("""
        SELECT DISTINCT inote 
        FROM InventoryNote inote 
        LEFT JOIN FETCH inote.branch 
        LEFT JOIN FETCH inote.partnerBranch 
        LEFT JOIN FETCH inote.supplier 
        LEFT JOIN FETCH inote.createdBy
        WHERE inote.type = :type AND inote.status = :status AND inote.branch.id = :branchId
        ORDER BY inote.createdAt DESC
    """)
    List<InventoryNote> findAllByTypeAndStatusAndBranchWithPartners(
            @Param("type") InventoryNoteType type,
            @Param("status") InventoryNoteStatus status,
            @Param("branchId") Long branchId
    );
    @Query("""
        SELECT DISTINCT inote 
        FROM InventoryNote inote 
        LEFT JOIN FETCH inote.branch 
        LEFT JOIN FETCH inote.partnerBranch 
        LEFT JOIN FETCH inote.supplier 
        LEFT JOIN FETCH inote.createdBy
        WHERE inote.type = :type AND inote.status IN :statuses
        ORDER BY inote.createdAt DESC
    """)
    List<InventoryNote> findAllByTypeAndStatusInWithPartners(
            @Param("type") InventoryNoteType type,
            @Param("statuses") Collection<InventoryNoteStatus> statuses
    );

    @Query("""
        SELECT DISTINCT inote 
        FROM InventoryNote inote 
        LEFT JOIN FETCH inote.branch 
        LEFT JOIN FETCH inote.partnerBranch 
        LEFT JOIN FETCH inote.supplier 
        LEFT JOIN FETCH inote.createdBy
        WHERE inote.type = :type AND inote.status IN :statuses AND inote.branch.id = :branchId
        ORDER BY inote.createdAt DESC
    """)
    List<InventoryNote> findAllByTypeAndStatusInAndBranchWithPartners(
            @Param("type") InventoryNoteType type,
            @Param("statuses") Collection<InventoryNoteStatus> statuses,
            @Param("branchId") Long branchId
    );

    // Kiểm tra có phiếu nào thuộc các trạng thái chỉ định không
    boolean existsByStatusIn(Collection<InventoryNoteStatus> statuses);

    // Kiểm tra sản phẩm có nằm trong phiếu với trạng thái chỉ định không
    @Query("""
        SELECT COUNT(in) > 0 
        FROM InventoryNote in 
        JOIN in.details detail 
        WHERE in.status IN :statuses 
        AND detail.productVariant.product.id = :productId
    """)
    boolean existsByStatusInAndProductId(
            @Param("statuses") Collection<InventoryNoteStatus> statuses,
            @Param("productId") Long productId
    );

    // Tìm phiếu theo code + fetch details (tránh LazyInitializationException khi trả về UI)
    @Query("""
        SELECT DISTINCT in 
        FROM InventoryNote in 
        LEFT JOIN FETCH in.details 
        LEFT JOIN FETCH in.branch
        LEFT JOIN FETCH in.supplier
        LEFT JOIN FETCH in.partnerBranch
        LEFT JOIN FETCH in.createdBy
        WHERE in.code = :code
    """)
    Optional<InventoryNote> findByCodeWithDetails(@Param("code") String code);

    @Query("""
        SELECT DISTINCT in 
        FROM InventoryNote in 
        LEFT JOIN FETCH in.details d
        LEFT JOIN FETCH d.productVariant
        LEFT JOIN FETCH in.branch
        LEFT JOIN FETCH in.supplier
        LEFT JOIN FETCH in.partnerBranch
        LEFT JOIN FETCH in.createdBy
        WHERE in.id = :id
    """)
    Optional<InventoryNote> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT in FROM InventoryNote in " +
            "LEFT JOIN FETCH in.createdBy " +
            "WHERE (:branchId IS NULL OR in.branch.id = :branchId) " +
            "ORDER BY in.createdAt DESC")
    List<InventoryNote> findRecentNotes(@Param("branchId") Long branchId, org.springframework.data.domain.Pageable pageable);

    // Lấy danh sách phiếu theo chi nhánh
    @Query("""
        SELECT in 
        FROM InventoryNote in 
        LEFT JOIN FETCH in.createdBy
        WHERE in.branch.id = :branchId 
        ORDER BY in.createdAt DESC
    """)
    List<InventoryNote> findAllByBranchId(@Param("branchId") Long branchId);

    // Lấy phiếu nhập của NCC, sắp xếp mới nhất lên đầu
    @Query("""
        SELECT DISTINCT note
        FROM InventoryNote note
        LEFT JOIN FETCH note.details detail
        LEFT JOIN FETCH detail.productVariant
        WHERE note.supplier.id = :supplierId
          AND note.type = :type
        ORDER BY note.createdAt DESC
    """)
    List<InventoryNote> findImportHistoryBySupplierId(
            @Param("supplierId") Long supplierId,
            @Param("type") InventoryNoteType type
    );

    @Query("""
        SELECT COALESCE(SUM(d.quantityRequested), 0)
        FROM InventoryNote n
        JOIN n.details d
        WHERE n.purchaseRequest.id = :purchaseRequestId
          AND d.productVariant.id = :variantId
          AND n.status IN :statuses
          AND (:excludeNoteId IS NULL OR n.id <> :excludeNoteId)
    """)
    long sumReservedQtyByPurchaseRequestAndVariantAndStatuses(
            @Param("purchaseRequestId") Long purchaseRequestId,
            @Param("variantId") Long variantId,
            @Param("statuses") Collection<InventoryNoteStatus> statuses,
            @Param("excludeNoteId") Long excludeNoteId
    );
}

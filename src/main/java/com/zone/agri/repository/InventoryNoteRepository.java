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
        WHERE in.code = :code
    """)
    Optional<InventoryNote> findByCodeWithDetails(@Param("code") String code);

    // Lấy danh sách phiếu theo chi nhánh
    @Query("""
        SELECT in 
        FROM InventoryNote in 
        WHERE in.branch.id = :branchId 
        ORDER BY in.createdAt DESC
    """)
    List<InventoryNote> findAllByBranchId(@Param("branchId") Long branchId);

    // Lấy phiếu nhập của NCC, sắp xếp mới nhất lên đầu
    List<InventoryNote> findBySupplierIdAndTypeOrderByCreatedAtDesc(Long supplierId, InventoryNoteType type);
}
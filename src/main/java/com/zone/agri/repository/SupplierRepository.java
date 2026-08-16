package com.zone.agri.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.SupplierStatus;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

        List<Supplier> findByStatus(SupplierStatus status);

        boolean existsByTaxCode(String taxCode);

        boolean existsByTaxCodeAndIdNot(String taxCode, Long id);

        Optional<Supplier> findByTaxCode(String taxCode);

        Optional<Supplier> findFirstByPhone(String phone);

        Optional<Supplier> findFirstByPhoneAndIdNot(String phone, Long id);

        Optional<Supplier> findFirstByEmailIgnoreCase(String email);

        Optional<Supplier> findFirstByEmailIgnoreCaseAndIdNot(String email, Long id);

        @Query("SELECT s FROM Supplier s WHERE " +
                        "(:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR s.taxCode LIKE CONCAT('%', :keyword, '%') " +
                        "OR s.phone LIKE CONCAT('%', :keyword, '%') " +
                        "OR LOWER(COALESCE(s.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:status IS NULL OR s.status = :status)")
        Page<Supplier> searchSuppliers(
                        @Param("keyword") String keyword,
                        @Param("status") SupplierStatus status,
                        Pageable pageable);

        @Query("SELECT s FROM Supplier s WHERE s.code = :code")
        Optional<Supplier> findByCode(@Param("code") String code);

        interface SupplierDebtSupplierProjection {
                Long getId();

                String getSupplierCode();

                String getSupplierName();

                String getPhone();
        }

        interface SupplierDebtLedgerProjection {
                Long getSupplierId();

                String getSupplierCode();

                String getSupplierName();

                String getPhone();

                Long getNoteId();

                String getNoteCode();

                String getBranchName();

                InventoryNoteType getNoteType();

                BigDecimal getTotalAmount();

                BigDecimal getPaidAmount();

                LocalDateTime getCreatedAt();

                Long getCreatedById();

                String getCreatedByName();
        }

        @Query("SELECT s.id AS id, s.code AS supplierCode, s.name AS supplierName, s.phone AS phone " +
                        "FROM Supplier s " +
                        "WHERE (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR s.code LIKE CONCAT('%', :search, '%') OR s.phone LIKE CONCAT('%', :search, '%'))")
        List<SupplierDebtSupplierProjection> findSuppliersForDebtReport(@Param("search") String search);

        @Query("SELECT i.supplier.id AS supplierId, i.supplier.code AS supplierCode, i.supplier.name AS supplierName, i.supplier.phone AS phone, i.id AS noteId, i.code AS noteCode, i.branch.name AS branchName, i.type AS noteType, " +
                        "COALESCE(i.totalAmount, 0) AS totalAmount, " +
                        "COALESCE((SELECT SUM(p.amount) FROM InventoryReceiptPayment p WHERE p.inventoryNote.id = i.id AND p.paymentDate <= :endDate), 0) AS paidAmount, " +
                        "i.createdAt AS createdAt, " +
                        "i.createdBy.id AS createdById, " +
                        "i.createdBy.fullName AS createdByName " +
                        "FROM InventoryNote i " +
                        "WHERE i.supplier IS NOT NULL " +
                        "AND i.status = com.zone.agri.entity.enums.InventoryNoteStatus.COMPLETED " +
                        "AND i.createdAt <= :endDate " +
                        "AND i.type IN (com.zone.agri.entity.enums.InventoryNoteType.IMPORT, com.zone.agri.entity.enums.InventoryNoteType.EXPORT) " +
                        "AND (:branchId IS NULL OR i.branch.id = :branchId) " +
                        "AND (:staffId IS NULL OR i.createdBy.id = :staffId) " +
                        "AND (:search IS NULL OR LOWER(i.supplier.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR i.supplier.code LIKE CONCAT('%', :search, '%') OR i.supplier.phone LIKE CONCAT('%', :search, '%'))")
        List<SupplierDebtLedgerProjection> findSupplierDebtLedger(
                        @Param("search") String search,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("branchId") Long branchId,
                        @Param("staffId") Long staffId);
}

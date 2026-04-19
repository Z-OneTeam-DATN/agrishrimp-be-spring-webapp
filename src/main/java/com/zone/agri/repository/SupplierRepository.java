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
import com.zone.agri.entity.enums.SupplierStatus;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

        boolean existsByTaxCode(String taxCode);

        Optional<Supplier> findByTaxCode(String taxCode);

        @Query("SELECT s FROM Supplier s WHERE " +
                        "(:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR s.taxCode LIKE CONCAT('%', :keyword, '%') " +
                        "OR s.phone LIKE CONCAT('%', :keyword, '%')) " +
                        "AND (:status IS NULL OR s.status = :status)")
        Page<Supplier> searchSuppliers(
                        @Param("keyword") String keyword,
                        @Param("status") SupplierStatus status,
                        Pageable pageable);

        @Query("SELECT s FROM Supplier s WHERE s.code = :code")
        Optional<Supplier> findByCode(@Param("code") String code);

        // Interface dùng để hứng dữ liệu từ câu Query
        interface SupplierDebtProjection {
                Long getId();

                String getSupplierCode();

                String getSupplierName();

                String getPhone();

                BigDecimal getTotalDebt();
        }

        @Query("SELECT s.id AS id, s.code AS supplierCode, s.name AS supplierName, s.phone AS phone, " +
                        "COALESCE(SUM(CASE " +
                        "WHEN ((i.type = 'IMPORT' AND i.status = 'COMPLETED') " +
                        "OR (i.type = 'EXPORT' AND i.status = 'COMPLETED' AND i.supplier IS NOT NULL)) " +
                        "AND (:startDate IS NULL OR i.createdAt >= :startDate) " +
                        "AND (:endDate IS NULL OR i.createdAt <= :endDate) " +
                        "AND (:branchId IS NULL OR i.branch.id = :branchId) " +
                        "AND (:staffId IS NULL OR i.createdBy.id = :staffId) " +
                        "THEN COALESCE(i.debtAmount, 0) ELSE 0 END), 0) AS totalDebt " +
                        "FROM Supplier s LEFT JOIN s.inventoryNotes i " +
                        "WHERE (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR s.code LIKE CONCAT('%', :search, '%') OR s.phone LIKE CONCAT('%', :search, '%')) " +
                        "GROUP BY s.id, s.code, s.name, s.phone")
        List<SupplierDebtProjection> findSuppliersWithDebt(
                        @Param("search") String search,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("branchId") Long branchId,
                        @Param("staffId") Long staffId);
}

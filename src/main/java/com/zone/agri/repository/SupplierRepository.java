package com.zone.agri.repository;

import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.SupplierStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByTaxCode(String taxCode);

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

    // Lấy danh sách NCC có công nợ (Dựa vào debtAmount trong InventoryNote)
    @Query("SELECT s.id AS id, s.code AS supplierCode, s.name AS supplierName, s.phone AS phone, " +
            "SUM(i.debtAmount) AS totalDebt " +
            "FROM Supplier s JOIN s.inventoryNotes i " +
            "WHERE i.type = 'IMPORT' " +
            "AND (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR s.code LIKE CONCAT('%', :search, '%') OR s.phone LIKE CONCAT('%', :search, '%')) " +
            "GROUP BY s.id, s.code, s.name, s.phone " +
            "HAVING SUM(i.debtAmount) > 0")
    List<SupplierDebtProjection> findSuppliersWithDebt(@Param("search") String search);
}
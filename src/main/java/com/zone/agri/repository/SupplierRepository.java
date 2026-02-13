package com.zone.agri.repository;

import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByTaxCode(String taxCode);
    boolean existsByCode(String code);

    @Query("SELECT s FROM Supplier s WHERE " +
            "(:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:categoryId IS NULL OR s.category.id = :categoryId) " + // Lọc theo ID danh mục
            "AND (:status IS NULL OR s.status = :status)")
    Page<Supplier> searchSuppliers(String keyword, Long categoryId, SupplierStatus status, Pageable pageable);
}
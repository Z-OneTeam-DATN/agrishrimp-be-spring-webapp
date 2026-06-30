package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.enums.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    java.util.Optional<Branch> findByBranchCode(String branchCode);
    java.util.Optional<Branch> findByPhone(String phone);

    List<Branch> findByStatus(BranchStatus status);

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.branchCode = :branchCode")
    boolean existsByBranchCode(@Param("branchCode") String branchCode);

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.phone = :phone")
    boolean existsByPhone(@Param("phone") String phone);

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.branchCode = :branchCode AND b.id <> :id")
    boolean existsByBranchCodeForUpdate(@Param("branchCode") String branchCode, @Param("id") Long id);

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.phone = :phone AND b.id <> :id")
    boolean existsByPhoneForUpdate(@Param("phone") String phone, @Param("id") Long id);

    @Query("SELECT (COALESCE(COUNT(DISTINCT s),0) + COALESCE(COUNT(DISTINCT r),0)) FROM Branch b " +
            "LEFT JOIN b.sentTransfers s " +
            "LEFT JOIN b.receivedTransfers r " +
            "WHERE b.id = :id")
    long countRelatedTransactions(@Param("id") Long id);

    @Query("SELECT b FROM Branch b WHERE b.name = :name")
    Optional<Branch> findByName(@Param("name") String name);

    List<Branch> findByStatusAndDistrictId(BranchStatus status, Integer districtId);

    List<Branch> findByStatusAndProvinceId(BranchStatus status, Integer provinceId);

    /**
     * Tầng 1 — Bounding Box filter.
     * Chỉ trả về chi nhánh ACTIVE đã được geocode và nằm trong hộp giới hạn.
     */
    @Query("""
            SELECT b FROM Branch b
            WHERE b.status = :status
              AND b.lat IS NOT NULL
              AND b.lng IS NOT NULL
              AND b.lat BETWEEN :minLat AND :maxLat
              AND b.lng BETWEEN :minLng AND :maxLng
            """)
    List<Branch> findBranchesInBoundingBox(
            @Param("status") BranchStatus status,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );
}
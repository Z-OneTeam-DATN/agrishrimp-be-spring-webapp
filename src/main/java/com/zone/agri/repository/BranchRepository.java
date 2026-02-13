package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.branchCode = :branchCode")
    boolean existsByBranchCode(@Param("branchCode") String branchCode);

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.phone = :phone")
    boolean existsByPhone(@Param("phone") String phone);

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.branchCode = :branchCode AND b.id <> :id")
    boolean existsByBranchCodeForUpdate(@Param("branchCode") String branchCode, @Param("id") Long id);

    @Query("SELECT COUNT(b) > 0 FROM Branch b WHERE b.phone = :phone AND b.id <> :id")
    boolean existsByPhoneForUpdate(@Param("phone") String phone, @Param("id") Long id);

    @Query("SELECT (COUNT(s) + COUNT(r)) FROM Branch b " +
            "LEFT JOIN b.sentTransfers s " +
            "LEFT JOIN b.receivedTransfers r " +
            "WHERE b.id = :id")
    long countRelatedTransactions(@Param("id") Long id);
}
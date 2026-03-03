package com.zone.agri.repository;

import com.zone.agri.entity.Handover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HandoverRepository extends JpaRepository<Handover, Long> {

    List<Handover> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    @Query("SELECT COUNT(h) FROM Handover h WHERE h.branch.id = :branchId AND DATE(h.createdAt) = CURRENT_DATE")
    long countByBranchIdToday(Long branchId);
}
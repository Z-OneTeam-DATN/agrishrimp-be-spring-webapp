package com.zone.agri.repository;

import com.zone.agri.entity.Handover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HandoverRepository extends JpaRepository<Handover, Long> {

    List<Handover> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    @Query("""
            SELECT COUNT(h)
            FROM Handover h
            WHERE h.branch.id = :branchId
              AND h.createdAt >= :startOfDay
              AND h.createdAt < :endOfDay
            """)
    long countByBranchIdBetween(Long branchId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    default long countByBranchIdToday(Long branchId) {
        LocalDate today = LocalDate.now();
        return countByBranchIdBetween(branchId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }
}

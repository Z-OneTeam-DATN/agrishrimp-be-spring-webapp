package com.zone.agri.repository;

import com.zone.agri.entity.SiteVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SiteVisitRepository extends JpaRepository<SiteVisit, Long> {

    @Query("SELECT COUNT(DISTINCT sv.visitorId) FROM SiteVisit sv WHERE sv.visitedAt BETWEEN :start AND :end")
    long countDistinctVisitors(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(sv) FROM SiteVisit sv WHERE sv.visitedAt BETWEEN :start AND :end")
    long countPageViews(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

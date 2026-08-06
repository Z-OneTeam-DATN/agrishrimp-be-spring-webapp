package com.zone.agri.repository;

import com.zone.agri.entity.Banner;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findAllByOrderByDisplayOrderAscCreatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Banner b
               set b.isActive = false
             where b.isActive = true
               and b.endDate is not null
               and b.endDate < :now
            """)
    int deactivateExpiredBanners(@Param("now") LocalDateTime now);
}

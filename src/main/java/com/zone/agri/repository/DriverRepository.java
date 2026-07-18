package com.zone.agri.repository;

import com.zone.agri.entity.Driver;
import com.zone.agri.entity.enums.DriverStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, Long id);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("SELECT d FROM Driver d WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(d.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.vehicleNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:status IS NULL OR d.status = :status)")
    Page<Driver> searchDrivers(@Param("keyword") String keyword, @Param("status") DriverStatus status, Pageable pageable);
}

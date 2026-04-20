package com.zone.agri.repository;

import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.VoucherStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.code = :code")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("SELECT v FROM Voucher v WHERE " +
            "(:keyword IS NULL OR LOWER(v.code) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:status IS NULL OR v.status = :status)")
    List<Voucher> searchVouchers(@Param("keyword") String keyword, @Param("status") VoucherStatus status);

    List<Voucher> findByStatus(VoucherStatus status);

    List<Voucher> findByStatusAndEndDateBefore(VoucherStatus status, LocalDateTime endDate);
}

package com.zone.agri.repository;

import com.zone.agri.entity.User;
import com.zone.agri.entity.UserVoucher;
import com.zone.agri.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    Optional<UserVoucher> findByUserAndVoucher(User user, Voucher voucher);

    @Query("""
            SELECT uv
            FROM UserVoucher uv
            JOIN FETCH uv.voucher v
            WHERE uv.user.id = :userId
              AND uv.isSaved = true
            ORDER BY uv.createdAt DESC
            """)
    List<UserVoucher> findSavedByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT uv
            FROM UserVoucher uv
            JOIN FETCH uv.voucher v
            WHERE uv.user.id = :userId
              AND uv.voucher.id IN :voucherIds
            """)
    List<UserVoucher> findByUserIdAndVoucherIds(
            @Param("userId") Long userId,
            @Param("voucherIds") Collection<Long> voucherIds);

    @Query("""
            SELECT uv
            FROM UserVoucher uv
            JOIN FETCH uv.voucher v
            WHERE uv.user.id = :userId
              AND v.code = :voucherCode
            """)
    Optional<UserVoucher> findByUserIdAndVoucherCode(
            @Param("userId") Long userId,
            @Param("voucherCode") String voucherCode);

    // 🟢 Voucher đã lưu, còn dùng được, sắp hết hạn — cho nhắc nhở qua thông báo/email
    @Query("""
            SELECT uv
            FROM UserVoucher uv
            JOIN FETCH uv.voucher v
            JOIN FETCH uv.user u
            WHERE uv.isSaved = true
              AND v.status = com.zone.agri.entity.enums.VoucherStatus.ACTIVE
              AND v.endDate BETWEEN :now AND :cutoff
              AND (v.maxUsagePerUser IS NULL OR uv.usageCount < v.maxUsagePerUser)
            """)
    List<UserVoucher> findActiveUnusedExpiringWithin(@Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);
}

package com.zone.agri.repository;

import com.zone.agri.entity.User;
import com.zone.agri.entity.UserVoucher;
import com.zone.agri.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    Optional<UserVoucher> findByUserAndVoucher(User user, Voucher voucher);
}
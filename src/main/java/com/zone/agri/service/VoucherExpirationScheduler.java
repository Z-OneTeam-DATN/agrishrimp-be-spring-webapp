package com.zone.agri.service;

import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoucherExpirationScheduler {

    private final VoucherRepository voucherRepository;

    @Transactional
    @Scheduled(cron = "0 0 0 * * ?")
    public void expireActiveVouchers() {
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> expiredVouchers = voucherRepository.findByStatusAndEndDateBefore(VoucherStatus.ACTIVE, now);
        if (expiredVouchers.isEmpty()) {
            return;
        }

        expiredVouchers.forEach(voucher -> voucher.setStatus(VoucherStatus.EXPIRED));
        voucherRepository.saveAll(expiredVouchers);
        log.info("Updated {} expired vouchers", expiredVouchers.size());
    }
}
package com.zone.agri.service;

import com.zone.agri.entity.UserVoucher;
import com.zone.agri.repository.UserVoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoucherExpiryReminderScheduler {

    private static final int REMINDER_WINDOW_DAYS = 3;

    private final UserVoucherRepository userVoucherRepository;
    private final NotificationService notificationService;

    @Transactional
    @Scheduled(cron = "0 0 8 * * ?")
    public void remindExpiringVouchers() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.plusDays(REMINDER_WINDOW_DAYS);

        List<UserVoucher> expiringSoon = userVoucherRepository.findActiveUnusedExpiringWithin(now, cutoff);
        if (expiringSoon.isEmpty()) {
            return;
        }

        for (UserVoucher userVoucher : expiringSoon) {
            try {
                int daysLeft = (int) ChronoUnit.DAYS.between(now.toLocalDate(),
                        userVoucher.getVoucher().getEndDate().toLocalDate());
                notificationService.notifyCustomerVoucherExpiringSoon(
                        userVoucher.getUser(), userVoucher.getVoucher(), Math.max(daysLeft, 0));
            } catch (Exception e) {
                log.warn("Failed to send voucher-expiring reminder for userVoucher {}: {}",
                        userVoucher.getId(), e.getMessage());
            }
        }
        log.info("Sent {} voucher-expiring reminders", expiringSoon.size());
    }
}

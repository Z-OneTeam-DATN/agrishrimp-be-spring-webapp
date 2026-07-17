package com.zone.agri.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyUserStatusMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer updatedRows = jdbcTemplate.update(
                    "UPDATE users SET status = 'INACTIVE' WHERE status = 'BANNED'");

            if (updatedRows != null && updatedRows > 0) {
                log.info("Da chuyen {} tai khoan BANNED cu sang INACTIVE.", updatedRows);
            }
        } catch (Exception ex) {
            log.warn("Khong the dong bo trang thai BANNED cu sang INACTIVE: {}", ex.getMessage());
        }
    }
}

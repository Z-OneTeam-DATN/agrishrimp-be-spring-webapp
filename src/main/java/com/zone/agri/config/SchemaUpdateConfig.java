package com.zone.agri.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Áp dụng các ALTER TABLE cần thiết khi Hibernate ddl-auto:update
 * không tự sửa được định nghĩa ENUM column trong MySQL.
 *
 * Mỗi patch phải idempotent (chạy nhiều lần vẫn an toàn).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SchemaUpdateConfig {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    ApplicationRunner applySchemaPatches() {
        return args -> {
            applyPatch(
                "Patch orders.payment_method ENUM thêm PAYOS",
                "ALTER TABLE orders MODIFY COLUMN payment_method ENUM('CASH','TRANSFER','COD','PAYOS')"
            );

            applyPatch(
                "Patch inventory_notes.type ENUM thêm CHECK",
                "ALTER TABLE inventory_notes MODIFY COLUMN type ENUM('IMPORT','EXPORT','CHECK')"
            );

            applyPatch(
                "Patch users.auth_provider ENUM thêm ZALO",
                "ALTER TABLE users MODIFY COLUMN auth_provider ENUM('LOCAL','GOOGLE','ZALO')"
            );
        };
    }

    private void applyPatch(String description, String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Schema patch OK — {}", description);
        } catch (Exception e) {
            // Nếu lỗi "Duplicate column" hoặc "already exists" → bỏ qua
            log.debug("Schema patch skipped/warn — {}: {}", description, e.getMessage());
        }
    }
}

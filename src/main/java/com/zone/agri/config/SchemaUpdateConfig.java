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
                "Patch orders.status ENUM thêm AWAITING_PAYMENT và READY_FOR_PICKUP",
                "ALTER TABLE orders MODIFY COLUMN status ENUM('PENDING','AWAITING_PAYMENT','AWAITING_REPLENISHMENT','CONFIRMED','PROCESSING','READY_FOR_PICKUP','SHIPPING','COMPLETED','CANCELLED','RETURNED')"
            );

            applyPatch(
                "Patch inventory_notes.type ENUM thêm CHECK",
                "ALTER TABLE inventory_notes MODIFY COLUMN type ENUM('IMPORT','EXPORT','CHECK')"
            );

            applyPatch(
                "Patch sub_orders.status length to 40",
                "ALTER TABLE sub_orders MODIFY COLUMN status VARCHAR(40)"
            );
            applyPatch(
                "Patch sub_order_items them allocated_quantity",
                "ALTER TABLE sub_order_items ADD COLUMN allocated_quantity INT NULL"
            );
            applyPatch(
                "Patch sub_order_items them missing_quantity",
                "ALTER TABLE sub_order_items ADD COLUMN missing_quantity INT NULL"
            );
            applyPatch(
                "Tạo bảng inventory_receipt_payments nếu chưa có",
                """
                CREATE TABLE IF NOT EXISTS inventory_receipt_payments (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    inventory_note_id BIGINT NOT NULL,
                    supplier_id BIGINT NULL,
                    branch_id BIGINT NULL,
                    created_by BIGINT NULL,
                    payment_date DATETIME NULL,
                    amount DECIMAL(38,2) NOT NULL DEFAULT 0,
                    remaining_debt_after DECIMAL(38,2) NOT NULL DEFAULT 0,
                    payment_method VARCHAR(50) NULL,
                    reference_code VARCHAR(255) NULL,
                    note TEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_irp_receipt (inventory_note_id),
                    INDEX idx_irp_payment_date (payment_date),
                    INDEX idx_irp_branch (branch_id)
                )
                """
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

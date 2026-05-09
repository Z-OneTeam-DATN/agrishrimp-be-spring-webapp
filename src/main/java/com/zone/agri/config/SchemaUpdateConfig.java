package com.zone.agri.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Applies idempotent schema patches that Hibernate update mode cannot handle
 * reliably in MySQL.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SchemaUpdateConfig {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @SuppressWarnings("unused")
    ApplicationRunner applySchemaPatches() {
        return args -> {
            applyPatch(
                    "Patch orders.payment_method enum adds PAYOS",
                    "ALTER TABLE orders MODIFY COLUMN payment_method ENUM('CASH','TRANSFER','COD','PAYOS')");

            applyPatch(
                    "Patch orders.status enum adds newer workflow states",
                    "ALTER TABLE orders MODIFY COLUMN status ENUM('PENDING','AWAITING_PAYMENT','AWAITING_REPLENISHMENT','CONFIRMED','PROCESSING','READY_FOR_PICKUP','SHIPPING','RECEIVED','COMPLETED','CANCELLED','RETURNED')");

            applyPatch(
                    "Patch inventory_notes.type enum adds CHECK",
                    "ALTER TABLE inventory_notes MODIFY COLUMN type ENUM('IMPORT','EXPORT','CHECK')");

            applyPatch(
                    "Patch sub_orders.status length to 40",
                    "ALTER TABLE sub_orders MODIFY COLUMN status VARCHAR(40)");
            applyPatch(
                    "Patch sub_order_items adds allocated_quantity",
                    "ALTER TABLE sub_order_items ADD COLUMN allocated_quantity INT NULL");
            applyPatch(
                    "Patch sub_order_items adds missing_quantity",
                    "ALTER TABLE sub_order_items ADD COLUMN missing_quantity INT NULL");
            applyPatch(
                    "Patch orders adds financial lifecycle timestamps",
                    "ALTER TABLE orders ADD COLUMN received_at DATETIME NULL, ADD COLUMN completed_at DATETIME NULL, ADD COLUMN returned_at DATETIME NULL, ADD COLUMN cancelled_at DATETIME NULL");
            applyPatch(
                    "Patch sub_orders adds financial lifecycle timestamps",
                    "ALTER TABLE sub_orders ADD COLUMN received_at DATETIME NULL, ADD COLUMN completed_at DATETIME NULL, ADD COLUMN returned_at DATETIME NULL, ADD COLUMN cancelled_at DATETIME NULL");
            applyPatch(
                    "Backfill lifecycle timestamps for existing orders",
                    """
                            UPDATE orders
                            SET received_at = COALESCE(received_at, CASE WHEN status IN ('RECEIVED', 'COMPLETED') THEN created_at END),
                                completed_at = COALESCE(completed_at, CASE WHEN status = 'COMPLETED' THEN created_at END),
                                returned_at = COALESCE(returned_at, CASE WHEN status = 'RETURNED' THEN created_at END),
                                cancelled_at = COALESCE(cancelled_at, CASE WHEN status = 'CANCELLED' THEN created_at END)
                            """);
            applyPatch(
                    "Backfill lifecycle timestamps for existing sub orders",
                    """
                            UPDATE sub_orders
                            SET received_at = COALESCE(received_at, CASE WHEN status IN ('RECEIVED', 'COMPLETED') THEN COALESCE(updated_at, created_at) END),
                                completed_at = COALESCE(completed_at, CASE WHEN status = 'COMPLETED' THEN COALESCE(updated_at, created_at) END),
                                returned_at = COALESCE(returned_at, CASE WHEN status = 'RETURNED' THEN COALESCE(updated_at, created_at) END),
                                cancelled_at = COALESCE(cancelled_at, CASE WHEN status = 'CANCELLED' THEN COALESCE(updated_at, created_at) END)
                            """);
            applyPatch(
                    "Create inventory_receipt_payments when missing",
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
                            """);

            applyPatch(
                    "Create supplier_product_catalogs when missing",
                    """
                            CREATE TABLE IF NOT EXISTS supplier_product_catalogs (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                supplier_id BIGINT NOT NULL,
                                product_id BIGINT NOT NULL,
                                status ENUM('AVAILABLE','UNAVAILABLE','CHECKING') NOT NULL DEFAULT 'CHECKING',
                                note TEXT NULL,
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                UNIQUE KEY uq_supplier_product_catalog (supplier_id, product_id),
                                INDEX idx_spc_supplier (supplier_id),
                                INDEX idx_spc_product (product_id)
                            )
                            """);
        };
    }

    private void applyPatch(String description, String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Schema patch OK - {}", description);
        } catch (Exception e) {
            log.debug("Schema patch skipped/warn - {}: {}", description, e.getMessage());
        }
    }
}

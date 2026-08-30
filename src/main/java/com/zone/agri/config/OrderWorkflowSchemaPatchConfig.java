package com.zone.agri.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs only the schema patches required by the order / payment workflow.
 * This stays enabled in local environments even when the broader startup
 * schema patch set is intentionally turned off.
 */
@Configuration
@ConditionalOnProperty(
        name = "app.startup.order-workflow-schema-patches.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class OrderWorkflowSchemaPatchConfig implements BeanPostProcessor {

    private final AtomicBoolean patched = new AtomicBoolean(false);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && patched.compareAndSet(false, true)) {
            log.info("DataSource bean '{}' detected. Running order workflow schema patches...", beanName);
            runPatches(dataSource);
        }
        return bean;
    }

    private void runPatches(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            executeSql(stmt,
                    "Patch orders.payment_method enum adds PAYOS",
                    "ALTER TABLE orders MODIFY COLUMN payment_method ENUM('CASH','TRANSFER','COD','PAYOS')");

            executeSql(stmt,
                    "Patch orders.payment_status enum adds workflow payment states",
                    "ALTER TABLE orders MODIFY COLUMN payment_status ENUM('UNPAID','PENDING','PENDING_VERIFICATION','PARTIALLY_PAID','PAID','FAILED','EXPIRED','REFUND_PENDING','REFUNDED')");

            executeSql(stmt,
                    "Patch orders.status enum adds replenishment workflow state",
                    "ALTER TABLE orders MODIFY COLUMN status ENUM('PENDING','AWAITING_PAYMENT','AWAITING_REPLENISHMENT','CONFIRMED','PROCESSING','READY_FOR_PICKUP','SHIPPING','RECEIVED','COMPLETED','CANCELLED','RETURNED')");

            executeSql(stmt,
                    "Patch inventory_transactions.type enum adds order reservation workflow types",
                    "ALTER TABLE inventory_transactions MODIFY COLUMN type ENUM('IMPORT','ORDER_RESERVE','ORDER_RELEASE','SALE','CANCEL_RELEASE','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT','RETURN','DAMAGED')");

            addColumnIfMissing(conn, stmt,
                    "orders",
                    "fulfillment_status",
                    "ALTER TABLE orders ADD COLUMN fulfillment_status VARCHAR(40) NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "stock_status",
                    "ALTER TABLE orders ADD COLUMN stock_status VARCHAR(40) NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "auto_approve_at",
                    "ALTER TABLE orders ADD COLUMN auto_approve_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "auto_approval_paused",
                    "ALTER TABLE orders ADD COLUMN auto_approval_paused BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "delivery_address_id",
                    "ALTER TABLE orders ADD COLUMN delivery_address_id BIGINT NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "version",
                    "ALTER TABLE orders ADD COLUMN version INT NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "updated_at",
                    "ALTER TABLE orders ADD COLUMN updated_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "received_at",
                    "ALTER TABLE orders ADD COLUMN received_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "shipping_started_at",
                    "ALTER TABLE orders ADD COLUMN shipping_started_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "completed_at",
                    "ALTER TABLE orders ADD COLUMN completed_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "returned_at",
                    "ALTER TABLE orders ADD COLUMN returned_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "cancelled_at",
                    "ALTER TABLE orders ADD COLUMN cancelled_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "cancel_reason_code",
                    "ALTER TABLE orders ADD COLUMN cancel_reason_code VARCHAR(50) NULL");
            addColumnIfMissing(conn, stmt,
                    "orders",
                    "cancel_reason_text",
                    "ALTER TABLE orders ADD COLUMN cancel_reason_text TEXT NULL");

            addColumnIfMissing(conn, stmt,
                    "branches",
                    "province_id",
                    "ALTER TABLE branches ADD COLUMN province_id INT NULL");
            addColumnIfMissing(conn, stmt,
                    "branches",
                    "province_name",
                    "ALTER TABLE branches ADD COLUMN province_name VARCHAR(100) NULL");
            addColumnIfMissing(conn, stmt,
                    "branches",
                    "district_id",
                    "ALTER TABLE branches ADD COLUMN district_id INT NULL");
            addColumnIfMissing(conn, stmt,
                    "branches",
                    "district_name",
                    "ALTER TABLE branches ADD COLUMN district_name VARCHAR(100) NULL");
            addColumnIfMissing(conn, stmt,
                    "branches",
                    "ward_id",
                    "ALTER TABLE branches ADD COLUMN ward_id INT NULL");
            addColumnIfMissing(conn, stmt,
                    "branches",
                    "ward_name",
                    "ALTER TABLE branches ADD COLUMN ward_name VARCHAR(100) NULL");
            addColumnIfMissing(conn, stmt,
                    "branches",
                    "ward_code",
                    "ALTER TABLE branches ADD COLUMN ward_code VARCHAR(20) NULL");
            addColumnIfMissing(conn, stmt,
                    "branches",
                    "lat",
                    "ALTER TABLE branches ADD COLUMN lat DOUBLE NULL");
            addColumnIfMissing(conn, stmt,
                    "branches",
                    "lng",
                    "ALTER TABLE branches ADD COLUMN lng DOUBLE NULL");

            executeSql(stmt,
                    "Patch sub_orders.status length to 40",
                    "ALTER TABLE sub_orders MODIFY COLUMN status VARCHAR(40)");

            addColumnIfMissing(conn, stmt,
                    "sub_orders",
                    "received_at",
                    "ALTER TABLE sub_orders ADD COLUMN received_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "sub_orders",
                    "shipping_started_at",
                    "ALTER TABLE sub_orders ADD COLUMN shipping_started_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "sub_orders",
                    "completed_at",
                    "ALTER TABLE sub_orders ADD COLUMN completed_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "sub_orders",
                    "returned_at",
                    "ALTER TABLE sub_orders ADD COLUMN returned_at DATETIME NULL");
            addColumnIfMissing(conn, stmt,
                    "sub_orders",
                    "cancelled_at",
                    "ALTER TABLE sub_orders ADD COLUMN cancelled_at DATETIME NULL");

            addColumnIfMissing(conn, stmt,
                    "sub_order_items",
                    "allocated_quantity",
                    "ALTER TABLE sub_order_items ADD COLUMN allocated_quantity INT NULL");
            addColumnIfMissing(conn, stmt,
                    "sub_order_items",
                    "missing_quantity",
                    "ALTER TABLE sub_order_items ADD COLUMN missing_quantity INT NULL");

            addColumnIfMissing(conn, stmt,
                    "purchase_requests",
                    "auto_replenishment",
                    "ALTER TABLE purchase_requests ADD COLUMN auto_replenishment BIT(1) NOT NULL DEFAULT b'0'");
            addColumnIfMissing(conn, stmt,
                    "purchase_requests",
                    "linked_sub_order_id",
                    "ALTER TABLE purchase_requests ADD COLUMN linked_sub_order_id BIGINT NULL");
            addColumnIfMissing(conn, stmt,
                    "purchase_requests",
                    "linked_destination_branch_id",
                    "ALTER TABLE purchase_requests ADD COLUMN linked_destination_branch_id BIGINT NULL");
            addColumnIfMissing(conn, stmt,
                    "purchase_requests",
                    "linked_reference_code",
                    "ALTER TABLE purchase_requests ADD COLUMN linked_reference_code VARCHAR(120) NULL");

            executeSql(stmt,
                    "Create return_requests when missing",
                    """
                            CREATE TABLE IF NOT EXISTS return_requests (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                created_at DATETIME NULL,
                                updated_at DATETIME NULL,
                                created_by_user_id BIGINT NULL,
                                updated_by_user_id BIGINT NULL,
                                code VARCHAR(30) NOT NULL,
                                status VARCHAR(40) NOT NULL,
                                issue_type VARCHAR(40) NOT NULL,
                                refund_method VARCHAR(40) NOT NULL,
                                requires_physical_return BIT(1) NOT NULL DEFAULT b'1',
                                customer_name VARCHAR(150) NOT NULL,
                                customer_phone VARCHAR(20) NOT NULL,
                                customer_email VARCHAR(150) NULL,
                                bank_account_name VARCHAR(150) NOT NULL,
                                bank_account_number VARCHAR(50) NOT NULL,
                                bank_name VARCHAR(150) NOT NULL,
                                bank_branch VARCHAR(150) NULL,
                                reason VARCHAR(255) NOT NULL,
                                description TEXT NOT NULL,
                                reject_reason TEXT NULL,
                                internal_note TEXT NULL,
                                total_refund_amount DECIMAL(38,2) NOT NULL DEFAULT 0,
                                approved_at DATETIME NULL,
                                rejected_at DATETIME NULL,
                                received_at DATETIME NULL,
                                refunded_at DATETIME NULL,
                                user_id BIGINT NOT NULL,
                                order_id BIGINT NOT NULL,
                                branch_id BIGINT NULL,
                                UNIQUE KEY uq_return_requests_code (code),
                                INDEX idx_return_requests_user (user_id),
                                INDEX idx_return_requests_order (order_id),
                                INDEX idx_return_requests_branch (branch_id),
                                INDEX idx_return_requests_status (status)
                            )
                            """);

            executeSql(stmt,
                    "Create return_request_items when missing",
                    """
                            CREATE TABLE IF NOT EXISTS return_request_items (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                created_at DATETIME NULL,
                                updated_at DATETIME NULL,
                                created_by_user_id BIGINT NULL,
                                updated_by_user_id BIGINT NULL,
                                source_type VARCHAR(40) NOT NULL,
                                source_item_id BIGINT NOT NULL,
                                product_variant_id BIGINT NULL,
                                sub_order_id BIGINT NULL,
                                product_name VARCHAR(255) NOT NULL,
                                variant_name VARCHAR(255) NULL,
                                sku VARCHAR(80) NULL,
                                image_url TEXT NULL,
                                quantity INT NOT NULL,
                                ordered_quantity INT NOT NULL,
                                unit_price DECIMAL(38,2) NOT NULL DEFAULT 0,
                                refund_amount DECIMAL(38,2) NOT NULL DEFAULT 0,
                                return_request_id BIGINT NOT NULL,
                                INDEX idx_return_request_items_request (return_request_id),
                                INDEX idx_return_request_items_variant (product_variant_id)
                            )
                            """);

            executeSql(stmt,
                    "Create return_request_evidences when missing",
                    """
                            CREATE TABLE IF NOT EXISTS return_request_evidences (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                created_at DATETIME NULL,
                                updated_at DATETIME NULL,
                                created_by_user_id BIGINT NULL,
                                updated_by_user_id BIGINT NULL,
                                media_type VARCHAR(20) NOT NULL,
                                file_url TEXT NOT NULL,
                                public_id VARCHAR(255) NULL,
                                file_name VARCHAR(255) NULL,
                                return_request_id BIGINT NOT NULL,
                                INDEX idx_return_request_evidences_request (return_request_id)
                            )
                            """);

            executeSql(stmt,
                    "Backfill orders.updated_at from created_at when missing",
                    """
                            UPDATE orders
                            SET updated_at = COALESCE(updated_at, created_at)
                            """);

            executeSql(stmt,
                    "Backfill lifecycle timestamps for existing orders",
                    """
                            UPDATE orders
                            SET received_at = COALESCE(received_at, CASE WHEN status IN ('RECEIVED', 'COMPLETED') THEN created_at END),
                                shipping_started_at = COALESCE(
                                    shipping_started_at,
                                    CASE
                                        WHEN status IN ('SHIPPING', 'RECEIVED', 'COMPLETED', 'RETURNED') THEN
                                            CASE
                                                WHEN received_at IS NULL THEN COALESCE(completed_at, updated_at, created_at)
                                                WHEN completed_at IS NULL THEN received_at
                                                WHEN received_at <= completed_at THEN received_at
                                                ELSE completed_at
                                            END
                                    END
                                ),
                                completed_at = COALESCE(completed_at, CASE WHEN status = 'COMPLETED' THEN created_at END),
                                returned_at = COALESCE(returned_at, CASE WHEN status = 'RETURNED' THEN created_at END),
                                cancelled_at = COALESCE(cancelled_at, CASE WHEN status = 'CANCELLED' THEN created_at END)
                            """);

            executeSql(stmt,
                    "Backfill lifecycle timestamps for existing sub orders",
                    """
                            UPDATE sub_orders
                            SET received_at = COALESCE(received_at, CASE WHEN status IN ('RECEIVED', 'COMPLETED') THEN COALESCE(updated_at, created_at) END),
                                shipping_started_at = COALESCE(
                                    shipping_started_at,
                                    CASE
                                        WHEN status IN ('SHIPPING', 'RECEIVED', 'COMPLETED', 'RETURNED') THEN
                                            CASE
                                                WHEN received_at IS NULL THEN COALESCE(completed_at, updated_at, created_at)
                                                WHEN completed_at IS NULL THEN received_at
                                                WHEN received_at <= completed_at THEN received_at
                                                ELSE completed_at
                                            END
                                    END
                                ),
                                completed_at = COALESCE(completed_at, CASE WHEN status = 'COMPLETED' THEN COALESCE(updated_at, created_at) END),
                                returned_at = COALESCE(returned_at, CASE WHEN status = 'RETURNED' THEN COALESCE(updated_at, created_at) END),
                                cancelled_at = COALESCE(cancelled_at, CASE WHEN status = 'CANCELLED' THEN COALESCE(updated_at, created_at) END)
                            """);

            log.info("Order workflow schema patches executed.");
        } catch (Exception e) {
            log.error("Failed to run order workflow schema patches", e);
        }
    }

    private void addColumnIfMissing(
            Connection conn,
            Statement stmt,
            String tableName,
            String columnName,
            String sql) {
        try {
            if (columnExists(conn, tableName, columnName)) {
                log.debug("Column {}.{} already exists, skip patch.", tableName, columnName);
                return;
            }
            stmt.execute(sql);
            log.info("Schema patch OK - added {}.{}", tableName, columnName);
        } catch (Exception e) {
            log.warn("Schema patch skipped/warn - add {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        } catch (Exception e) {
            log.debug("Failed to inspect {}.{}: {}", tableName, columnName, e.getMessage());
            return false;
        }
    }

    private void executeSql(Statement stmt, String description, String sql) {
        try {
            stmt.execute(sql);
            log.info("Schema patch OK - {}", description);
        } catch (Exception e) {
            log.debug("Schema patch skipped/warn - {}: {}", description, e.getMessage());
        }
    }
}

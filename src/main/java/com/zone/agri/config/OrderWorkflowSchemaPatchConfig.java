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
                    "received_at",
                    "ALTER TABLE orders ADD COLUMN received_at DATETIME NULL");
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

            executeSql(stmt,
                    "Patch sub_orders.status length to 40",
                    "ALTER TABLE sub_orders MODIFY COLUMN status VARCHAR(40)");

            addColumnIfMissing(conn, stmt,
                    "sub_orders",
                    "received_at",
                    "ALTER TABLE sub_orders ADD COLUMN received_at DATETIME NULL");
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

            executeSql(stmt,
                    "Backfill lifecycle timestamps for existing orders",
                    """
                            UPDATE orders
                            SET received_at = COALESCE(received_at, CASE WHEN status IN ('RECEIVED', 'COMPLETED') THEN created_at END),
                                completed_at = COALESCE(completed_at, CASE WHEN status = 'COMPLETED' THEN created_at END),
                                returned_at = COALESCE(returned_at, CASE WHEN status = 'RETURNED' THEN created_at END),
                                cancelled_at = COALESCE(cancelled_at, CASE WHEN status = 'CANCELLED' THEN created_at END)
                            """);

            executeSql(stmt,
                    "Backfill lifecycle timestamps for existing sub orders",
                    """
                            UPDATE sub_orders
                            SET received_at = COALESCE(received_at, CASE WHEN status IN ('RECEIVED', 'COMPLETED') THEN COALESCE(updated_at, created_at) END),
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
            log.debug("Schema patch skipped/warn - add {}.{}: {}", tableName, columnName, e.getMessage());
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

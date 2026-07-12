package com.zone.agri.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Applies idempotent schema patches that Hibernate update mode cannot handle
 * reliably in MySQL. This implements BeanPostProcessor for DataSource to ensure
 * patches run BEFORE Hibernate starts schema validation.
 */
@Configuration
@ConditionalOnProperty(name = "app.startup.schema-patches.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SchemaUpdateConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource) {
            log.info("DataSource bean '{}' detected. Running database schema patches...", beanName);
            runPatches((DataSource) bean);
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
                    "Patch orders.status enum adds newer workflow states",
                    "ALTER TABLE orders MODIFY COLUMN status ENUM('PENDING','AWAITING_PAYMENT','AWAITING_REPLENISHMENT','CONFIRMED','PROCESSING','READY_FOR_PICKUP','SHIPPING','RECEIVED','COMPLETED','CANCELLED','RETURNED')");

            executeSql(stmt,
                    "Patch inventory_notes.type enum adds CHECK",
                    "ALTER TABLE inventory_notes MODIFY COLUMN type ENUM('IMPORT','EXPORT','CHECK')");

            executeSql(stmt,
                    "Patch sub_orders.status length to 40",
                    "ALTER TABLE sub_orders MODIFY COLUMN status VARCHAR(40)");

            executeSql(stmt,
                    "Patch sub_order_items adds allocated_quantity",
                    "ALTER TABLE sub_order_items ADD COLUMN allocated_quantity INT NULL");

            executeSql(stmt,
                    "Patch sub_order_items adds missing_quantity",
                    "ALTER TABLE sub_order_items ADD COLUMN missing_quantity INT NULL");

            executeSql(stmt,
                    "Patch orders adds financial lifecycle timestamps",
                    "ALTER TABLE orders ADD COLUMN received_at DATETIME NULL, ADD COLUMN completed_at DATETIME NULL, ADD COLUMN returned_at DATETIME NULL, ADD COLUMN cancelled_at DATETIME NULL");

            executeSql(stmt,
                    "Patch sub_orders adds financial lifecycle timestamps",
                    "ALTER TABLE sub_orders ADD COLUMN received_at DATETIME NULL, ADD COLUMN completed_at DATETIME NULL, ADD COLUMN returned_at DATETIME NULL, ADD COLUMN cancelled_at DATETIME NULL");

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

            executeSql(stmt,
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

            // Detect old schema (using product_id) and drop it to migrate to SKU variants
            try {
                stmt.execute("SELECT product_id FROM supplier_product_catalogs LIMIT 1");
                log.info("Old catalog table schema detected. Dropping supplier_product_catalogs to recreate for SKU migration.");
                stmt.execute("DROP TABLE IF EXISTS supplier_product_catalogs");
            } catch (Exception e) {
                // Table doesn't exist or already migrated (no product_id column)
            }

            executeSql(stmt,
                    "Create supplier_product_catalogs when missing",
                    """
                            CREATE TABLE IF NOT EXISTS supplier_product_catalogs (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                supplier_id BIGINT NOT NULL,
                                product_variant_id BIGINT NOT NULL,
                                status ENUM('AVAILABLE','UNAVAILABLE','CHECKING') NOT NULL DEFAULT 'CHECKING',
                                note TEXT NULL,
                                status_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                version INT NOT NULL DEFAULT 0,
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                created_by_user_id BIGINT NULL,
                                updated_by_user_id BIGINT NULL,
                                UNIQUE KEY uq_supplier_product_catalog_variant (supplier_id, product_variant_id),
                                INDEX idx_spc_supplier (supplier_id),
                                INDEX idx_spc_product_variant (product_variant_id)
                            )
                            """);

            executeSql(stmt,
                    "Patch suppliers adds created_by_user_id",
                    "ALTER TABLE suppliers ADD COLUMN created_by_user_id BIGINT NULL");

            executeSql(stmt,
                    "Patch suppliers adds updated_by_user_id",
                    "ALTER TABLE suppliers ADD COLUMN updated_by_user_id BIGINT NULL");

            executeSql(stmt,
                    "Patch suppliers adds issue_date",
                    "ALTER TABLE suppliers ADD COLUMN issue_date DATE NULL");

            executeSql(stmt,
                    "Patch suppliers adds tax_authority",
                    "ALTER TABLE suppliers ADD COLUMN tax_authority VARCHAR(255) NULL");

            executeSql(stmt,
                    "Patch suppliers adds main_business_sector",
                    "ALTER TABLE suppliers ADD COLUMN main_business_sector TEXT NULL");

            executeSql(stmt,
                    "Patch products adds supplier_id column",
                    "ALTER TABLE products ADD COLUMN supplier_id BIGINT NULL");

            executeSql(stmt,
                    "Patch banners adds mobile_image_url",
                    "ALTER TABLE banners ADD COLUMN mobile_image_url TEXT NULL");

            executeSql(stmt,
                    "Patch banners adds mobile_public_id",
                    "ALTER TABLE banners ADD COLUMN mobile_public_id VARCHAR(255) NULL");

            executeSql(stmt,
                    "Patch blog_categories adds status",
                    "ALTER TABLE blog_categories ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'");

            executeSql(stmt,
                    "Backfill blog_categories.status nulls to ACTIVE",
                    "UPDATE blog_categories SET status = 'ACTIVE' WHERE status IS NULL OR TRIM(status) = ''");

            executeSql(stmt,
                    "Patch products drops origin column",
                    "ALTER TABLE products DROP COLUMN origin");

            // Safe drop foreign key and column for supplier_id in products table
            dropForeignKeyIfExists(stmt, "products", "supplier_id");
            executeSql(stmt,
                    "Patch products drops supplier_id column",
                    "ALTER TABLE products DROP COLUMN supplier_id");

            // Add brand_id column and foreign key to products
            executeSql(stmt,
                    "Patch products adds brand_id column",
                    "ALTER TABLE products ADD COLUMN brand_id BIGINT NULL");
            executeSql(stmt,
                    "Patch products adds fk_products_brand constraint",
                    "ALTER TABLE products ADD CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands(id)");

            executeSql(stmt,
                    "Patch brands increase logo_url size to TEXT",
                    "ALTER TABLE brands MODIFY COLUMN logo_url TEXT NULL");

            executeSql(stmt,
                    "Seed default cashflow risk settings in system_settings if missing",
                    """
                            INSERT IGNORE INTO system_settings (setting_key, setting_value, description) VALUES
                            ('CASHFLOW_RISK_WINDOW_DAYS', '14', 'Cửa sổ thời gian xét rủi ro dòng tiền (ngày)'),
                            ('CASHFLOW_CRITICAL_THRESHOLD_PERCENT', '20.0', 'Tỷ lệ cảnh báo đỏ rủi ro dòng tiền (%)'),
                            ('CASHFLOW_WEIGHT_TIME', '0.5', 'Trọng số mức độ gấp thời gian của công nợ'),
                            ('CASHFLOW_WEIGHT_FREQUENCY', '0.3', 'Trọng số tần suất nhập hàng từ nhà cung cấp'),
                            ('CASHFLOW_WEIGHT_VALUE', '0.2', 'Trọng số giá trị tuyệt đối của khoản nợ'),
                            ('SUPPLIER_DEBT_DEFAULT_TERM_DAYS', '30', 'Kỳ hạn nợ mặc định của phiếu nhập nếu không cấu hình (ngày)')
                            """);

            log.info("All schema patches executed successfully.");
        } catch (Exception e) {
            log.error("Failed to run database schema patches", e);
        }
    }

    private void dropForeignKeyIfExists(Statement stmt, String tableName, String columnName) {
        try {
            java.sql.ResultSet rs = stmt.executeQuery(String.format(
                "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '%s' AND COLUMN_NAME = '%s' " +
                "AND REFERENCED_TABLE_NAME IS NOT NULL", tableName, columnName));
            if (rs.next()) {
                String constraintName = rs.getString("CONSTRAINT_NAME");
                stmt.execute(String.format("ALTER TABLE %s DROP FOREIGN KEY %s", tableName, constraintName));
                log.info("Dropped foreign key {} on {}.{}", constraintName, tableName, columnName);
            }
            rs.close();
        } catch (Exception e) {
            log.debug("Skipped dropping foreign key on {}.{}: {}", tableName, columnName, e.getMessage());
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

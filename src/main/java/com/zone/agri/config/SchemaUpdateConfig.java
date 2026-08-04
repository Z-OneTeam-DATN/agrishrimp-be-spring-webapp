package com.zone.agri.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

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
                    "Patch orders.payment_status enum adds workflow payment states",
                    "ALTER TABLE orders MODIFY COLUMN payment_status ENUM('UNPAID','PENDING','PENDING_VERIFICATION','PARTIALLY_PAID','PAID','FAILED','EXPIRED','REFUND_PENDING','REFUNDED')");

            executeSql(stmt,
                    "Patch orders.status enum adds newer workflow states",
                    "ALTER TABLE orders MODIFY COLUMN status ENUM('PENDING','AWAITING_PAYMENT','AWAITING_REPLENISHMENT','CONFIRMED','PROCESSING','READY_FOR_PICKUP','SHIPPING','RECEIVED','COMPLETED','CANCELLED','RETURNED')");

            executeSql(stmt,
                    "Patch inventory_transactions.type enum adds order reservation workflow types",
                    "ALTER TABLE inventory_transactions MODIFY COLUMN type ENUM('IMPORT','ORDER_RESERVE','ORDER_RELEASE','SALE','CANCEL_RELEASE','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT','RETURN','DAMAGED')");

            executeSql(stmt,
                    "Patch orders adds workflow state columns",
                    "ALTER TABLE orders ADD COLUMN fulfillment_status VARCHAR(40) NULL, ADD COLUMN stock_status VARCHAR(40) NULL, ADD COLUMN auto_approve_at DATETIME NULL, ADD COLUMN auto_approval_paused BIT(1) NOT NULL DEFAULT b'0', ADD COLUMN delivery_address_id BIGINT NULL, ADD COLUMN version INT NOT NULL DEFAULT 0");

            executeSql(stmt,
                    "Patch inventory_notes.type enum adds CHECK",
                    "ALTER TABLE inventory_notes MODIFY COLUMN type ENUM('IMPORT','EXPORT','CHECK')");

            patchBranches(conn, stmt);
            patchUsers(conn, stmt);
            patchCustomers(conn, stmt);

            executeSql(stmt,
                    "Patch inventory_notes adds check_scope_type",
                    "ALTER TABLE inventory_notes ADD COLUMN check_scope_type VARCHAR(50) NULL");

            executeSql(stmt,
                    "Patch inventory_notes adds check_started_at",
                    "ALTER TABLE inventory_notes ADD COLUMN check_started_at DATETIME NULL");

            executeSql(stmt,
                    "Patch inventory_notes adds check_recount_reason",
                    "ALTER TABLE inventory_notes ADD COLUMN check_recount_reason TEXT NULL");

            executeSql(stmt,
                    "Patch inventory_notes adds check_cancel_reason",
                    "ALTER TABLE inventory_notes ADD COLUMN check_cancel_reason TEXT NULL");

            executeSql(stmt,
                    "Patch inventory_notes adds check_cancelled_at",
                    "ALTER TABLE inventory_notes ADD COLUMN check_cancelled_at DATETIME NULL");

            executeSql(stmt,
                    "Patch inventory_notes widens check_workflow_status",
                    "ALTER TABLE inventory_notes MODIFY COLUMN check_workflow_status VARCHAR(50) NULL");

            executeSql(stmt,
                    "Backfill inventory_notes.check_scope_type for legacy check notes",
                    "UPDATE inventory_notes SET check_scope_type = 'FULL_WAREHOUSE' WHERE type = 'CHECK' AND (check_scope_type IS NULL OR TRIM(check_scope_type) = '')");

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

            ensureColumnWithLegacyBackfill(conn, stmt,
                    "orders",
                    "cancel_reason_code",
                    "VARCHAR(50) NULL",
                    List.of());

            ensureColumnWithLegacyBackfill(conn, stmt,
                    "orders",
                    "cancel_reason_text",
                    "TEXT NULL",
                    List.of());

            ensureColumnWithLegacyBackfill(conn, stmt,
                    "orders",
                    "updated_at",
                    "DATETIME NULL",
                    List.of("created_at"));

            executeSql(stmt,
                    "Patch sub_orders adds financial lifecycle timestamps",
                    "ALTER TABLE sub_orders ADD COLUMN received_at DATETIME NULL, ADD COLUMN completed_at DATETIME NULL, ADD COLUMN returned_at DATETIME NULL, ADD COLUMN cancelled_at DATETIME NULL");

            executeSql(stmt,
                    "Patch purchase_requests adds auto replenishment tracking columns",
                    "ALTER TABLE purchase_requests ADD COLUMN auto_replenishment BIT(1) NOT NULL DEFAULT b'0', " +
                            "ADD COLUMN linked_sub_order_id BIGINT NULL, " +
                            "ADD COLUMN linked_destination_branch_id BIGINT NULL, " +
                            "ADD COLUMN linked_reference_code VARCHAR(120) NULL");

            patchInventoryTransfers(conn, stmt);

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
                    "Create product_recommendations when missing",
                    """
                            CREATE TABLE IF NOT EXISTS product_recommendations (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                product_id BIGINT NOT NULL,
                                recommended_product_id BIGINT NOT NULL,
                                support_count INT NOT NULL,
                                customer_count INT NOT NULL,
                                support DECIMAL(12,6) NOT NULL,
                                confidence DECIMAL(12,6) NOT NULL,
                                lift DECIMAL(12,6) NOT NULL,
                                calculated_at DATETIME NOT NULL,
                                UNIQUE KEY uq_product_recommendation_pair (product_id, recommended_product_id),
                                INDEX idx_product_recommendations_product (product_id),
                                INDEX idx_product_recommendations_rank (product_id, lift, confidence)
                            )
                            """);

            executeSql(stmt,
                    "Create product_recommendation_clicks when missing",
                    """
                            CREATE TABLE IF NOT EXISTS product_recommendation_clicks (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                product_id BIGINT NOT NULL,
                                recommended_product_id BIGINT NOT NULL,
                                source VARCHAR(80) NULL,
                                clicked_at DATETIME NOT NULL,
                                INDEX idx_recommendation_clicks_product (product_id),
                                INDEX idx_recommendation_clicks_recommended (recommended_product_id),
                                INDEX idx_recommendation_clicks_clicked_at (clicked_at)
                            )
                            """);

            executeSql(stmt,
                    "Create site_visits when missing",
                    """
                            CREATE TABLE IF NOT EXISTS site_visits (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                visitor_id VARCHAR(64) NOT NULL,
                                path VARCHAR(500) NULL,
                                user_agent VARCHAR(500) NULL,
                                visited_at DATETIME NOT NULL,
                                INDEX idx_site_visits_visited_at (visited_at),
                                INDEX idx_site_visits_visitor_id (visitor_id)
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
                    "Patch conversations adds last_sender_id",
                    "ALTER TABLE conversations ADD COLUMN last_sender_id BIGINT NULL");

            executeSql(stmt,
                    "Patch miniapp_diagnosis_history adds status",
                    "ALTER TABLE miniapp_diagnosis_history ADD COLUMN status VARCHAR(20) NULL");

            executeSql(stmt,
                    "Patch miniapp_diagnosis_history adds ai_description",
                    "ALTER TABLE miniapp_diagnosis_history ADD COLUMN ai_description TEXT NULL");

            executeSql(stmt,
                    "Seed default cashflow risk settings in system_settings if missing",
                    """
                            INSERT IGNORE INTO system_settings (setting_key, setting_value, description) VALUES
                            ('CASHFLOW_RISK_WINDOW_DAYS', '14', 'Cửa sổ thời gian xét rủi ro dòng tiền (ngày)'),
                            ('CASHFLOW_CRITICAL_THRESHOLD_PERCENT', '20.0', 'Tỷ lệ cảnh báo đỏ rủi ro dòng tiền (%)'),
                            ('CASHFLOW_WEIGHT_TIME', '0.5', 'Trọng số mức độ gấp thời gian của công nợ'),
                            ('CASHFLOW_WEIGHT_FREQUENCY', '0.3', 'Trọng số tần suất nhập hàng từ nhà cung cấp'),
                            ('CASHFLOW_WEIGHT_VALUE', '0.2', 'Trọng số giá trị tuyệt đối của khoản nợ'),
                            ('SUPPLIER_DEBT_DEFAULT_TERM_DAYS', '30', 'Kỳ hạn nợ mặc định của phiếu nhập nếu không cấu hình (ngày)'),
                            ('DEBT_AGE_WARNING_DAYS', '45', 'Ngưỡng tuổi nợ cảnh báo công nợ NCC (ngày)'),
                            ('DEBT_AGE_CRITICAL_DAYS', '90', 'Ngưỡng tuổi nợ nghiêm trọng công nợ NCC (ngày)'),
                            ('DEBT_WEIGHT_AGE', '0.5', 'Trọng số tuổi nợ trong điểm ưu tiên thanh toán'),
                            ('DEBT_WEIGHT_VALUE', '0.5', 'Trọng số giá trị nợ trong điểm ưu tiên thanh toán')
                            """);

            executeSql(stmt,
                    "Create sticker_packs table",
                    "CREATE TABLE IF NOT EXISTS sticker_packs (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "icon_url VARCHAR(255), " +
                    "created_at DATETIME, " +
                    "updated_at DATETIME, " +
                    "created_by_user_id BIGINT, " +
                    "updated_by_user_id BIGINT" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            executeSql(stmt,
                    "Create stickers table",
                    "CREATE TABLE IF NOT EXISTS stickers (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "pack_id BIGINT NOT NULL, " +
                    "url VARCHAR(255) NOT NULL, " +
                    "label VARCHAR(100), " +
                    "created_at DATETIME, " +
                    "updated_at DATETIME, " +
                    "created_by_user_id BIGINT, " +
                    "updated_by_user_id BIGINT, " +
                    "FOREIGN KEY (pack_id) REFERENCES sticker_packs(id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            executeSql(stmt,
                    "Create drivers table",
                    """
                            CREATE TABLE IF NOT EXISTS drivers (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                code VARCHAR(20) NOT NULL UNIQUE,
                                full_name VARCHAR(255) NOT NULL,
                                phone VARCHAR(20) NULL,
                                email VARCHAR(100) NULL,
                                id_card VARCHAR(50) NULL,
                                license_number VARCHAR(50) NULL,
                                license_class VARCHAR(20) NULL,
                                avatar_url TEXT NULL,
                                license_image_url TEXT NULL,
                                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                vehicle_number VARCHAR(50) NULL,
                                vehicle_type VARCHAR(100) NULL,
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                created_by_user_id BIGINT NULL,
                                updated_by_user_id BIGINT NULL
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                            """);

            executeSql(stmt,
                    "Drop obsolete table unit_conversions if exists",
                    "DROP TABLE IF EXISTS unit_conversions");

            executeSql(stmt,
                    "Drop obsolete table units if exists",
                    "DROP TABLE IF EXISTS units");

            executeSql(stmt,
                    "Drop obsolete table canned_responses if exists",
                    "DROP TABLE IF EXISTS canned_responses");

            executeSql(stmt,
                    "Drop obsolete table customer_internal_notes if exists",
                    "DROP TABLE IF EXISTS customer_internal_notes");

            executeSql(stmt,
                    "Drop obsolete table customer_status_logs if exists",
                    "DROP TABLE IF EXISTS customer_status_logs");

            executeSql(stmt,
                    "Drop obsolete table images if exists",
                    "DROP TABLE IF EXISTS images");

            executeSql(stmt,
                    "Drop obsolete columns geocoded_at, district_id, district_name from branches",
                    "ALTER TABLE branches DROP COLUMN geocoded_at, DROP COLUMN district_id, DROP COLUMN district_name");

            executeSql(stmt,
                    "Drop obsolete columns internal_notes, note from customers",
                    "ALTER TABLE customers DROP COLUMN internal_notes, DROP COLUMN note");

            executeSql(stmt,
                    "Drop obsolete column zalo_id from users",
                    "ALTER TABLE users DROP COLUMN zalo_id");

            executeSql(stmt,
                    "Patch ai_disease_knowledge adds review_note",
                    "ALTER TABLE ai_disease_knowledge ADD COLUMN review_note TEXT NULL");

            log.info("All schema patches executed successfully.");
        } catch (Exception e) {
            log.error("Failed to run database schema patches", e);
        }
    }

    private void patchBranches(Connection conn, Statement stmt) {
        String tableName = "branches";
        if (!tableExists(conn, tableName)) {
            log.info("Skip branches schema patch because table '{}' does not exist yet.", tableName);
            return;
        }

        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "branch_type", "VARCHAR(20) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "address_detail", "TEXT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "full_address", "TEXT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "map_display_name", "TEXT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "province_id", "INT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "province_name", "VARCHAR(100) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "district_id", "INT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "district_name", "VARCHAR(100) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "ward_id", "INT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "ward_name", "VARCHAR(100) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "lat", "DOUBLE NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "lng", "DOUBLE NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "ward_code", "VARCHAR(20) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "geocoded_at", "DATETIME(6) NULL", List.of());
    }

    private void patchUsers(Connection conn, Statement stmt) {
        String tableName = "users";
        if (!tableExists(conn, tableName)) {
            log.info("Skip users schema patch because table '{}' does not exist yet.", tableName);
            return;
        }

        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "citizen_id", "VARCHAR(12) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "date_of_birth", "DATE NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "address_detail", "TEXT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "start_date", "DATE NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "gender", "TINYINT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "deleted_at", "DATETIME(6) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "zalo_id", "VARCHAR(255) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "auth_provider", "VARCHAR(50) NULL", List.of("provider"));

        executeSql(stmt,
                "Backfill users.auth_provider to LOCAL",
                "UPDATE users SET auth_provider = 'LOCAL' WHERE auth_provider IS NULL OR TRIM(auth_provider) = ''");
    }

    private void patchCustomers(Connection conn, Statement stmt) {
        String tableName = "customers";
        if (!tableExists(conn, tableName)) {
            log.info("Skip customers schema patch because table '{}' does not exist yet.", tableName);
            return;
        }

        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "gender", "VARCHAR(30) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "province_id", "VARCHAR(255) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "district_id", "VARCHAR(255) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "ward_id", "VARCHAR(255) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "address_detail", "VARCHAR(255) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "status", "VARCHAR(30) NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "note", "TEXT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "user_id", "BIGINT NULL", List.of());
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "branch_id", "BIGINT NULL", List.of("assigned_branch_id"));
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "staff_assigned_id", "BIGINT NULL", List.of("assigned_staff_id"));
        ensureColumnWithLegacyBackfill(conn, stmt, tableName, "internal_notes", "TEXT NULL", List.of());
    }

    private void patchInventoryTransfers(Connection conn, Statement stmt) {
        String tableName = "inventory_transfers";
        if (!tableExists(conn, tableName)) {
            log.info("Skip inventory transfer schema patch because table '{}' does not exist yet.", tableName);
            return;
        }

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "created_by_user_id",
                "BIGINT NULL",
                List.of("created_by"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "created_by_branch_id",
                "BIGINT NULL",
                List.of("created_by_branch", "branch_id"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "transfer_business_type",
                "VARCHAR(30) NULL",
                List.of("business_type"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "transfer_amount",
                "DECIMAL(38,2) NULL",
                List.of("total_transfer_amount", "amount"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "settlement_status",
                "VARCHAR(20) NULL",
                List.of("payment_status"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "source_receivable_amount",
                "DECIMAL(38,2) NULL",
                List.of("receivable_amount", "source_amount_receivable"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "dest_payable_amount",
                "DECIMAL(38,2) NULL",
                List.of("payable_amount", "destination_payable_amount", "dest_amount_payable"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "paid_amount",
                "DECIMAL(38,2) NULL",
                List.of("amount_paid"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "source_confirmed_by_user_id",
                "BIGINT NULL",
                List.of("source_confirmed_by", "confirmed_by"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "source_confirmed_at",
                "DATETIME NULL",
                List.of("source_confirmed_time", "confirmed_at", "source_confirmed_date"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "approved_by_user_id",
                "BIGINT NULL",
                List.of("approved_by"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "approved_at",
                "DATETIME NULL",
                List.of("approval_at", "approved_date"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "shipped_by_user_id",
                "BIGINT NULL",
                List.of("shipped_by"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "shipped_at",
                "DATETIME NULL",
                List.of("shipping_at", "shipped_date"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "inspection_started_by_user_id",
                "BIGINT NULL",
                List.of("inspection_started_by", "checked_by", "inspected_by"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "inspection_started_at",
                "DATETIME NULL",
                List.of("inspection_started_time", "inspection_started_date", "checked_at", "inspected_at"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "received_by_user_id",
                "BIGINT NULL",
                List.of("received_by"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "received_at",
                "DATETIME NULL",
                List.of("received_date"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "settled_by_user_id",
                "BIGINT NULL",
                List.of("settled_by", "paid_by"));

        ensureColumnWithLegacyBackfill(conn, stmt, tableName,
                "settled_at",
                "DATETIME NULL",
                List.of("settlement_at", "paid_at", "settled_date"));

        executeSql(stmt,
                "Backfill inventory_transfers.transfer_business_type to STOCK_TRANSFER",
                """
                        UPDATE inventory_transfers
                        SET transfer_business_type = 'STOCK_TRANSFER'
                        WHERE transfer_business_type IS NULL OR TRIM(transfer_business_type) = ''
                        """);

        addForeignKeyIfMissing(conn, stmt, tableName, "created_by_user_id", "users", "id", "fk_inventory_transfers_created_by_user");
        addForeignKeyIfMissing(conn, stmt, tableName, "created_by_branch_id", "branches", "id", "fk_inventory_transfers_created_by_branch");
        addForeignKeyIfMissing(conn, stmt, tableName, "source_confirmed_by_user_id", "users", "id", "fk_inventory_transfers_source_confirmed_by_user");
        addForeignKeyIfMissing(conn, stmt, tableName, "approved_by_user_id", "users", "id", "fk_inventory_transfers_approved_by_user");
        addForeignKeyIfMissing(conn, stmt, tableName, "shipped_by_user_id", "users", "id", "fk_inventory_transfers_shipped_by_user");
        addForeignKeyIfMissing(conn, stmt, tableName, "inspection_started_by_user_id", "users", "id", "fk_inventory_transfers_inspection_started_by_user");
        addForeignKeyIfMissing(conn, stmt, tableName, "received_by_user_id", "users", "id", "fk_inventory_transfers_received_by_user");
        addForeignKeyIfMissing(conn, stmt, tableName, "settled_by_user_id", "users", "id", "fk_inventory_transfers_settled_by_user");
    }

    private void ensureColumnWithLegacyBackfill(
            Connection conn,
            Statement stmt,
            String tableName,
            String targetColumn,
            String columnDefinition,
            List<String> legacyColumns) {
        if (!columnExists(conn, tableName, targetColumn)) {
            executeSql(stmt,
                    "Patch " + tableName + " adds " + targetColumn,
                    "ALTER TABLE " + tableName + " ADD COLUMN " + targetColumn + " " + columnDefinition);
        }

        for (String legacyColumn : legacyColumns) {
            if (!columnExists(conn, tableName, targetColumn)) {
                return;
            }
            if (!columnExists(conn, tableName, legacyColumn)) {
                continue;
            }
            executeSql(stmt,
                    "Backfill " + tableName + "." + targetColumn + " from " + legacyColumn,
                    "UPDATE " + tableName + " SET " + targetColumn + " = COALESCE(" + targetColumn + ", " + legacyColumn + ")");
            return;
        }
    }

    private boolean tableExists(Connection conn, String tableName) {
        String sql = """
                SELECT 1
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.debug("Failed checking table {} existence: {}", tableName, e.getMessage());
            return false;
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) {
        String sql = """
                SELECT 1
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.debug("Failed checking column {}.{} existence: {}", tableName, columnName, e.getMessage());
            return false;
        }
    }

    private boolean foreignKeyExists(Connection conn, String tableName, String constraintName) {
        String sql = """
                SELECT 1
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.debug("Failed checking foreign key {} on {}: {}", constraintName, tableName, e.getMessage());
            return false;
        }
    }

    private void addForeignKeyIfMissing(
            Connection conn,
            Statement stmt,
            String tableName,
            String columnName,
            String referencedTable,
            String referencedColumn,
            String constraintName) {
        if (!columnExists(conn, tableName, columnName) || foreignKeyExists(conn, tableName, constraintName)) {
            return;
        }

        executeSql(stmt,
                "Patch " + tableName + " adds foreign key " + constraintName,
                "ALTER TABLE " + tableName
                        + " ADD CONSTRAINT " + constraintName
                        + " FOREIGN KEY (" + columnName + ") REFERENCES "
                        + referencedTable + "(" + referencedColumn + ")");
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

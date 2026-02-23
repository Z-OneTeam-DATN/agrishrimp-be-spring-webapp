package com.zone.agri.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu một endpoint yêu cầu permission cụ thể.
 *
 * <p>Ví dụ sử dụng:</p>
 * <pre>
 *   {@code @RequirePermission("PRODUCT_CREATE")}
 *   {@code @PostMapping("/products")}
 *   public ResponseEntity<?> createProduct(...) { ... }
 * </pre>
 *
 * <p>Nhiều permission (OR logic — có một trong số là đủ):</p>
 * <pre>
 *   {@code @RequirePermission({"IMPORT_MANAGE", "IMPORT_APPROVE"})}
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /** Một hoặc nhiều permission code. Người dùng cần có ÍT NHẤT MỘT trong số này. */
    String[] value();
}

package com.zone.agri.repository;

import com.zone.agri.dto.response.activity.ActivityLogResponse;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ActivityLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    @PostConstruct
    void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS activity_logs (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    actor_user_id BIGINT NULL,
                    actor_name VARCHAR(150) NULL,
                    actor_role_slug VARCHAR(80) NULL,
                    branch_id BIGINT NULL,
                    module VARCHAR(80) NOT NULL,
                    action_code VARCHAR(40) NOT NULL,
                    permission_code VARCHAR(120) NULL,
                    target_type VARCHAR(120) NULL,
                    target_id VARCHAR(120) NULL,
                    target_label VARCHAR(255) NULL,
                    message VARCHAR(500) NOT NULL,
                    http_method VARCHAR(20) NULL,
                    request_path VARCHAR(255) NULL,
                    ip_address VARCHAR(64) NULL,
                    user_agent VARCHAR(255) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    INDEX idx_activity_logs_created_at (created_at),
                    INDEX idx_activity_logs_actor_created (actor_user_id, created_at),
                    INDEX idx_activity_logs_branch_created (branch_id, created_at),
                    INDEX idx_activity_logs_module_created (module, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    public void save(
            Long actorUserId,
            String actorName,
            String actorRoleSlug,
            Long branchId,
            String module,
            String action,
            String permissionCode,
            String targetType,
            String targetId,
            String targetLabel,
            String message,
            String httpMethod,
            String requestPath,
            String ipAddress,
            String userAgent) {
        Map<String, Object> params = new HashMap<>();
        params.put("actorUserId", actorUserId);
        params.put("actorName", actorName);
        params.put("actorRoleSlug", actorRoleSlug);
        params.put("branchId", branchId);
        params.put("module", module);
        params.put("action", action);
        params.put("permissionCode", permissionCode);
        params.put("targetType", targetType);
        params.put("targetId", targetId);
        params.put("targetLabel", targetLabel);
        params.put("message", message);
        params.put("httpMethod", httpMethod);
        params.put("requestPath", requestPath);
        params.put("ipAddress", ipAddress);
        params.put("userAgent", userAgent);
        params.put("createdAt", LocalDateTime.now());

        namedJdbcTemplate.update("""
                INSERT INTO activity_logs (
                    actor_user_id, actor_name, actor_role_slug, branch_id,
                    module, action_code, permission_code, target_type, target_id, target_label,
                    message, http_method, request_path, ip_address, user_agent, created_at
                ) VALUES (
                    :actorUserId, :actorName, :actorRoleSlug, :branchId,
                    :module, :action, :permissionCode, :targetType, :targetId, :targetLabel,
                    :message, :httpMethod, :requestPath, :ipAddress, :userAgent, :createdAt
                )
                """, params);
    }

    public Page<ActivityLogResponse> search(
            Long actorUserId,
            Long branchId,
            String module,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String keyword,
            Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (actorUserId != null) {
            where.append(" AND al.actor_user_id = :actorUserId ");
            params.put("actorUserId", actorUserId);
        }
        if (branchId != null) {
            where.append(" AND al.branch_id = :branchId ");
            params.put("branchId", branchId);
        }
        if (module != null && !module.isBlank()) {
            where.append(" AND al.module = :module ");
            params.put("module", module.trim().toUpperCase());
        }
        if (fromDate != null) {
            where.append(" AND al.created_at >= :fromDate ");
            params.put("fromDate", fromDate);
        }
        if (toDate != null) {
            where.append(" AND al.created_at <= :toDate ");
            params.put("toDate", toDate);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
                    AND (
                        LOWER(al.actor_name) LIKE :keyword OR
                        LOWER(al.message) LIKE :keyword OR
                        LOWER(al.target_label) LIKE :keyword OR
                        LOWER(al.request_path) LIKE :keyword
                    )
                    """);
            params.put("keyword", "%" + keyword.trim().toLowerCase() + "%");
        }

        Long total = namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs al " + where,
                params,
                Long.class);

        params.put("limit", pageable.getPageSize());
        params.put("offset", pageable.getOffset());

        List<ActivityLogResponse> content = namedJdbcTemplate.query("""
                        SELECT al.*, b.name AS branch_name
                        FROM activity_logs al
                        LEFT JOIN branches b ON b.id = al.branch_id
                        """ + where + """
                        ORDER BY al.created_at DESC, al.id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                params,
                (rs, rowNum) -> ActivityLogResponse.builder()
                        .id(rs.getLong("id"))
                        .actorUserId(getNullableLong(rs.getObject("actor_user_id")))
                        .actorName(rs.getString("actor_name"))
                        .actorRoleSlug(rs.getString("actor_role_slug"))
                        .branchId(getNullableLong(rs.getObject("branch_id")))
                        .branchName(rs.getString("branch_name"))
                        .module(rs.getString("module"))
                        .action(rs.getString("action_code"))
                        .permissionCode(rs.getString("permission_code"))
                        .targetType(rs.getString("target_type"))
                        .targetId(rs.getString("target_id"))
                        .targetLabel(rs.getString("target_label"))
                        .message(rs.getString("message"))
                        .httpMethod(rs.getString("http_method"))
                        .requestPath(rs.getString("request_path"))
                        .ipAddress(rs.getString("ip_address"))
                        .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                        .build());

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public List<String> findDistinctModules() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT module FROM activity_logs ORDER BY module",
                String.class);
    }

    private Long getNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

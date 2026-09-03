package com.zone.agri.common;

import java.util.Set;

public final class RoleUtils {

    private static final Set<String> ADMIN_LIKE_ROLES = Set.of("ADMIN", "SUPER_ADMIN");
    private static final Set<String> ADMIN_LIKE_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    private static final Set<String> SUPER_ADMIN_AUTHORITIES = Set.of("ROLE_SUPER_ADMIN");

    private RoleUtils() {
    }

    public static String normalizeRoleSlug(String roleSlug) {
        if (roleSlug == null) {
            return "";
        }

        String normalized = roleSlug.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    public static boolean isAdminLikeRole(String roleSlug) {
        return ADMIN_LIKE_ROLES.contains(normalizeRoleSlug(roleSlug));
    }

    public static boolean isSuperAdminRole(String roleSlug) {
        return "SUPER_ADMIN".equals(normalizeRoleSlug(roleSlug));
    }

    public static boolean hasAdminLikeAuthority(Set<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        return authorities.stream()
                .map(RoleUtils::normalizeRoleSlug)
                .map(slug -> "ROLE_" + slug)
                .anyMatch(ADMIN_LIKE_AUTHORITIES::contains);
    }

    public static boolean hasSuperAdminAuthority(Set<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        return authorities.stream()
                .map(RoleUtils::normalizeRoleSlug)
                .map(slug -> "ROLE_" + slug)
                .anyMatch(SUPER_ADMIN_AUTHORITIES::contains);
    }
}

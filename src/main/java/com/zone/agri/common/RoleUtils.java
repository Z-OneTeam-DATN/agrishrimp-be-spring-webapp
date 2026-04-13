package com.zone.agri.common;

import java.util.Set;

public final class RoleUtils {

    private static final Set<String> ADMIN_LIKE_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "ADMINISTRATOR");
    private static final Set<String> ADMIN_LIKE_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_ADMINISTRATOR");

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

    public static boolean hasAdminLikeAuthority(Set<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }

        return authorities.stream()
                .map(RoleUtils::normalizeRoleSlug)
                .map(slug -> "ROLE_" + slug)
                .anyMatch(ADMIN_LIKE_AUTHORITIES::contains);
    }
}

package com.zone.agri.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.dto.request.user.RoleRequest;
import com.zone.agri.dto.response.user.RoleResponse;
import com.zone.agri.entity.Permission;
import com.zone.agri.entity.Role;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.PermissionRepository;
import com.zone.agri.repository.RoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private static final Map<String, String> LEGACY_PERMISSION_ALIASES;

    static {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("CHECK_VIEW", "INVENTORY_CHECK_VIEW");
        aliases.put("CHECK_CREATE", "INVENTORY_CHECK_CREATE");
        aliases.put("CHECK_UPDATE", "INVENTORY_CHECK_UPDATE");
        aliases.put("CHECK_APPROVE", "INVENTORY_CHECK_APPROVE");
        aliases.put("CHECK_CANCEL", "INVENTORY_CHECK_CANCEL");
        aliases.put("CHECK_DELETE", "INVENTORY_CHECK_DELETE");
        LEGACY_PERMISSION_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        Role role = saveRoleFromRequest(new Role(), request);
        return mapToResponse(roleRepository.save(role), 0L);
    }

    public Page<RoleResponse> getAllRoles(String keyword, String type, String status, Pageable pageable) {
        Boolean isSystem = null;
        if ("system".equalsIgnoreCase(type))
            isSystem = true;
        else if ("custom".equalsIgnoreCase(type))
            isSystem = false;

        Boolean isActive = null;
        if ("active".equalsIgnoreCase(status)) {
            isActive = true;
        } else if ("inactive".equalsIgnoreCase(status)) {
            isActive = false;
        }

        String searchKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<Role> rolesPage = roleRepository.findAllWithFilter(searchKeyword, isSystem, isActive, pageable);
        Map<Long, Long> memberCountMap = getMemberCountMap(rolesPage.getContent());

        return rolesPage.map(role -> mapToResponse(role, memberCountMap.getOrDefault(role.getId(), 0L)));
    }

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional
    public RoleResponse updateRole(Long roleId, RoleRequest request) {
        Role existingRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò với ID: " + roleId));

        if (Boolean.TRUE.equals(existingRole.getIsSystem())) {
            throw new Forbidden("Không thể chỉnh sửa vai trò hệ thống");
        }

        saveRoleFromRequest(existingRole, request);
        long memberCount = roleRepository.countUsersByRoleId(roleId);
        return mapToResponse(roleRepository.save(existingRole), memberCount);
    }

    public RoleResponse getRoleById(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò với ID: " + roleId));
        long memberCount = roleRepository.countUsersByRoleId(roleId);
        return mapToResponse(role, memberCount);
    }

    @Transactional
    public void deleteRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò với ID: " + roleId));

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new Forbidden("Không thể xóa vai trò hệ thống");
        }

        long userCount = roleRepository.countUsersByRoleId(roleId);
        if (userCount > 0) {
            throw new ConflictException("Không thể xóa vai trò đang có " + userCount + " nhân viên sử dụng");
        }

        roleRepository.delete(role);
    }

    private Role saveRoleFromRequest(Role role, RoleRequest request) {
        if (request.getRoleName() == null || request.getRoleName().isBlank()) {
            throw new BadRequestException("Tên vai trò không được để trống");
        }

        String normalizedRoleName = request.getRoleName().trim();
        String requestedSlug = normalizedRoleName.toUpperCase().replace(" ", "_");
        String currentRoleName = role.getDisplayName() == null ? "" : role.getDisplayName().trim();
        boolean isCreate = role.getId() == null;
        boolean isNameChanged = isCreate || !normalizedRoleName.equalsIgnoreCase(currentRoleName);

        if (isNameChanged) {
            boolean isConflict = isCreate
                    ? roleRepository.existsBySlug(requestedSlug)
                    : roleRepository.existsBySlugAndIdNot(requestedSlug, role.getId());

            if (isConflict) {
                throw new ConflictException("Tên vai trò này đã tồn tại trong hệ thống");
            }
        }

        List<String> allCodes = new ArrayList<>();
        if (request.getEnabledScreens() != null)
            allCodes.addAll(request.getEnabledScreens());
        if (request.getAdvancedPerms() != null)
            allCodes.addAll(request.getAdvancedPerms());

        if (allCodes.isEmpty()) {
            throw new BadRequestException("Vai trò phải có ít nhất một quyền được gán");
        }

        List<String> normalizedCodes = allCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .map(code -> LEGACY_PERMISSION_ALIASES.getOrDefault(code, code))
                .distinct()
                .toList();

        if (normalizedCodes.isEmpty()) {
            throw new BadRequestException("Danh sách quyền không hợp lệ");
        }

        Set<Permission> permissions = permissionRepository.findAllByCodeIn(normalizedCodes);
        Set<String> resolvedCodes = permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        Set<String> invalidCodes = normalizedCodes.stream()
                .filter(code -> !resolvedCodes.contains(code))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!invalidCodes.isEmpty()) {
            throw new BadRequestException("Mã quyền không tồn tại: " + String.join(", ", invalidCodes));
        }

        if (isCreate || isNameChanged) {
            role.setSlug(requestedSlug);
        }
        role.setDisplayName(normalizedRoleName);
        role.setDescription(request.getDescription());
        role.setIsActive("active".equalsIgnoreCase(request.getStatus()));
        role.setIsSystem(Boolean.TRUE.equals(role.getIsSystem()));
        role.setPermissions(permissions);

        return role;
    }

    private Map<Long, Long> getMemberCountMap(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> roleIds = roles.stream()
                .map(Role::getId)
                .toList();

        return roleRepository.countUsersByRoleIds(roleIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    private RoleResponse mapToResponse(Role role, Long memberCount) {
        return RoleResponse.builder()
                .id(role.getId())
                .displayName(role.getDisplayName())
                .slug(role.getSlug())
                .description(role.getDescription())
                .isActive(role.getIsActive())
                .isSystem(role.getIsSystem())
                .memberCount(memberCount)
                .permissionCodes(role.getPermissions().stream()
                        .map(Permission::getCode)
                        .collect(Collectors.toList()))
                .build();
    }
}

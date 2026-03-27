package com.zone.agri.service;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        Role role = saveRoleFromRequest(new Role(), request);
        return mapToResponse(roleRepository.save(role));
    }

    public Page<RoleResponse> getAllRoles(String keyword, String type, String status, Pageable pageable) {
        Boolean isSystem = null;
        if ("system".equalsIgnoreCase(type)) isSystem = true;
        else if ("custom".equalsIgnoreCase(type)) isSystem = false;

        Boolean isActive = null;
        if ("active".equalsIgnoreCase(status)) {
            isActive = true;
        } else if ("inactive".equalsIgnoreCase(status)) {
            isActive = false;
        }

        String searchKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        return roleRepository.findAllWithFilter(searchKeyword, isSystem, isActive, pageable)
                .map(this::mapToResponse);
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
        return mapToResponse(roleRepository.save(existingRole));
    }

    public RoleResponse getRoleById(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò với ID: " + roleId));
        return mapToResponse(role);
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

        String slug = request.getRoleName().trim().toUpperCase().replace(" ", "_");
        
        boolean isConflict;
        if (role.getId() == null) {
            isConflict = roleRepository.existsBySlug(slug);
        } else {
            isConflict = roleRepository.existsBySlugAndIdNot(slug, role.getId());
        }

        if (isConflict) {
            throw new ConflictException("Tên vai trò này đã tồn tại trong hệ thống");
        }

        List<String> allCodes = new ArrayList<>();
        if (request.getEnabledScreens() != null) allCodes.addAll(request.getEnabledScreens());
        if (request.getAdvancedPerms() != null) allCodes.addAll(request.getAdvancedPerms());

        if (allCodes.isEmpty()) {
            throw new BadRequestException("Vai trò phải có ít nhất một quyền được gán");
        }

        Set<Permission> permissions = permissionRepository.findAllByCodeIn(allCodes);
        if (permissions.isEmpty()) {
            throw new BadRequestException("Danh sách quyền không hợp lệ");
        }

        role.setSlug(slug);
        role.setDisplayName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setIsActive("active".equalsIgnoreCase(request.getStatus()));
        role.setIsSystem(role.getIsSystem() != null ? role.getIsSystem() : false);
        role.setPermissions(permissions);
        
        return role;
    }

    private RoleResponse mapToResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .displayName(role.getDisplayName())
                .slug(role.getSlug())
                .description(role.getDescription())
                .isActive(role.getIsActive())
                .isSystem(role.getIsSystem())
                .permissionCodes(role.getPermissions().stream()
                        .map(Permission::getCode)
                        .collect(Collectors.toList()))
                .build();
    }
}

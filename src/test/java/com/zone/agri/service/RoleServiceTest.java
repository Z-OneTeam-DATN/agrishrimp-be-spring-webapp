package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zone.agri.dto.request.user.RoleRequest;
import com.zone.agri.dto.response.user.RoleDto;
import com.zone.agri.dto.response.user.RoleResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Permission;
import com.zone.agri.entity.Role;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.repository.PermissionRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.security.CustomUserDetail;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, permissionRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateRole_withSystemRoleAndSuperAdmin_updatesOnlyPermissions() {
        authenticateAs("ROLE_SUPER_ADMIN");
        Role adminRole = systemRole(2L, "ADMIN", "Quan tri vien", "Mo ta cu", permission("ROLE_VIEW"));
        Permission roleView = permission("ROLE_VIEW");
        Permission roleUpdate = permission("ROLE_UPDATE");

        when(roleRepository.findById(2L)).thenReturn(Optional.of(adminRole));
        when(permissionRepository.findAllByCodeIn(List.of("ROLE_VIEW", "ROLE_UPDATE")))
                .thenReturn(new HashSet<>(Set.of(roleView, roleUpdate)));
        when(roleRepository.countUsersByRoleId(2L)).thenReturn(1L);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse response = roleService.updateRole(2L, roleRequest("Ten moi khong duoc doi"));

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        Role savedRole = captor.getValue();

        assertThat(savedRole.getDisplayName()).isEqualTo("Quan tri vien");
        assertThat(savedRole.getDescription()).isEqualTo("Mo ta cu");
        assertThat(permissionCodes(savedRole)).containsExactlyInAnyOrder("ROLE_VIEW", "ROLE_UPDATE");
        assertThat(response.getPermissionCodes()).containsExactlyInAnyOrder("ROLE_VIEW", "ROLE_UPDATE");
    }

    @Test
    void updateRole_withSystemRoleAndAdmin_throwsForbidden() {
        authenticateAs("ROLE_ADMIN");
        Role adminRole = systemRole(2L, "ADMIN", "Quan tri vien", "Mo ta cu", permission("ROLE_VIEW"));

        when(roleRepository.findById(2L)).thenReturn(Optional.of(adminRole));

        assertThatThrownBy(() -> roleService.updateRole(2L, roleRequest("Quan tri vien")))
                .isInstanceOf(Forbidden.class)
                .hasMessageContaining("Chỉ SUPER_ADMIN");

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void updateRole_withSuperAdminRole_throwsForbidden() {
        authenticateAs("ROLE_SUPER_ADMIN");
        Role superAdminRole = systemRole(1L, "SUPER_ADMIN", "Sieu quan tri vien", "Mo ta cu", permission("ROLE_VIEW"));

        when(roleRepository.findById(1L)).thenReturn(Optional.of(superAdminRole));

        assertThatThrownBy(() -> roleService.updateRole(1L, roleRequest("Sieu quan tri vien")))
                .isInstanceOf(Forbidden.class)
                .hasMessageContaining("SUPER_ADMIN");

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void updateRole_withCurrentUserRole_throwsForbidden() {
        Role customRole = Role.builder()
                .id(3L)
                .slug("MANAGER")
                .displayName("Quan ly")
                .description("Mo ta cu")
                .isSystem(false)
                .isActive(true)
                .permissions(new HashSet<>(Set.of(permission("ROLE_VIEW"))))
                .build();

        authenticateAsUserWithRole(customRole, "ROLE_MANAGER", "ROLE_UPDATE");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(customRole));

        assertThatThrownBy(() -> roleService.updateRole(3L, roleRequest("Quan ly")))
                .isInstanceOf(Forbidden.class)
                .hasMessageContaining("chính bạn");

        verify(roleRepository, never()).save(any(Role.class));
    }

    private void authenticateAs(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tester@agrishrimp.vn",
                        null,
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private void authenticateAsUserWithRole(Role role, String... authorities) {
        UserDetail userDetail = UserDetail.builder()
                .id(99L)
                .email("tester@agrishrimp.vn")
                .role(new RoleDto(role))
                .build();

        List<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();

        CustomUserDetail principal = new CustomUserDetail(
                userDetail.getEmail(),
                "password",
                true,
                true,
                userDetail,
                new HashSet<>(grantedAuthorities));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, grantedAuthorities));
    }

    private RoleRequest roleRequest(String roleName) {
        return RoleRequest.builder()
                .roleName(roleName)
                .description("Mo ta moi khong duoc doi")
                .status("inactive")
                .enabledScreens(List.of("ROLE_VIEW"))
                .advancedPerms(List.of("ROLE_UPDATE"))
                .build();
    }

    private Role systemRole(Long id, String slug, String displayName, String description, Permission permission) {
        return Role.builder()
                .id(id)
                .slug(slug)
                .displayName(displayName)
                .description(description)
                .isSystem(true)
                .isActive(true)
                .permissions(new HashSet<>(Set.of(permission)))
                .build();
    }

    private Permission permission(String code) {
        return Permission.builder()
                .code(code)
                .name(code)
                .build();
    }

    private Set<String> permissionCodes(Role role) {
        return role.getPermissions().stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
    }
}

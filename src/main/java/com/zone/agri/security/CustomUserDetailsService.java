package com.zone.agri.security;

import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.response.user.RoleDto;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Permission;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.repository.PermissionRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // 1. Tìm user theo email hoặc SĐT
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại hoặc mật khẩu sai"));

        // 2. Build danh sách quyền từ Role
        Set<GrantedAuthority> authorities = buildAuthorities(user);

        // 3. Map user sang DTO
        UserDetail userDetailDto = UserDetail.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .contact(user.getEmail() != null ? user.getEmail() : user.getPhoneNumber()) // Linh động email hoặc sđt
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                .role(user.getRole() != null ? new RoleDto(user.getRole()) : null)
                .tokenVersion(user.getTokenVersion() != null ? user.getTokenVersion() : 0)
                .build();

        // 4. Xử lý trạng thái tài khoản
        boolean enabled = (user.getStatus() == UserStatus.ACTIVE);
        boolean accountNonLocked = true;

        return new CustomUserDetail(username, user.getPasswordHash(), enabled, accountNonLocked, userDetailDto, authorities);
    }

    /**
     * Chuyển đổi Role + Permissions sang Spring Security GrantedAuthority.
     * <ul>
     *   <li>{@code ROLE_<SLUG>} — dùng với {@code hasRole()} / {@code @PreAuthorize("hasRole('ADMIN')")}</li>
     *   <li>{@code <PERMISSION_CODE>} — dùng với {@code hasAuthority()} / {@code @RequirePermission}</li>
     * </ul>
     */
    private Set<GrantedAuthority> buildAuthorities(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (user.getRole() == null) return authorities;

        // Role-level authority: "ROLE_ADMIN", "ROLE_CUSTOMER", ...
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getSlug()));

        // Permission-level authorities: "PRODUCT_CREATE", "IMPORT_APPROVE", ...
        if (RoleUtils.isSuperAdminRole(user.getRole().getSlug())) {
            permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .filter(code -> code != null && !code.isBlank())
                    .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        } else if (user.getRole().getPermissions() != null) {
            user.getRole().getPermissions().forEach(permission ->
                    authorities.add(new SimpleGrantedAuthority(permission.getCode()))
            );
        }

        return authorities;
    }
}

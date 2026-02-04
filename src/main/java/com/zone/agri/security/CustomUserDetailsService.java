package com.zone.agri.security;

import com.zone.agri.dto.user.UserDetail;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // 1. TÌM KIẾM THÔNG MINH (Email hoặc SĐT)
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại hoặc mật khẩu sai"));

        // 2. MAP DỮ LIỆU SANG DTO (UserDetail)
        UserDetail userDetailDto = UserDetail.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())

                // Xử lý Branch (tránh NullPointerException nếu user chưa có chi nhánh)
                .branchId(user.getBranch() != null ? user.getBranch().getId() : null)

                // Role xử lý bên UserDetail.getAuthorities(), không cần map list ở đây nữa
                .build();

        // 3. XỬ LÝ TRẠNG THÁI (Status)
        UserStatus status = user.getStatus();
        boolean enabled = (status == UserStatus.ACTIVE);
        boolean accountNonLocked = (status != UserStatus.BANNED);

        // 4. TRẢ VỀ ĐỐI TƯỢNG CHO SPRING SECURITY
        return new CustomUserDetail(
                username,
                user.getPasswordHash(),
                enabled,
                accountNonLocked,
                userDetailDto
        );
    }
}
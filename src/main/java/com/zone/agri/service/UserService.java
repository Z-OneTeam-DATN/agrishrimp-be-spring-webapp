package com.zone.agri.service;

import com.zone.agri.dto.user.UserRequest;
import com.zone.agri.dto.user.UserResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.Gender;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<UserResponse> getUsers(String keyword, Long roleId, Long branchId, String status, Pageable pageable) {
        UserStatus userStatus = null;
        if (status != null && !status.equalsIgnoreCase("all")) {
            try {
                userStatus = UserStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return userRepository.findAllWithFilter(keyword, roleId, branchId, userStatus, pageable)
                .map(this::mapUserToResponse);
    }

    public UserResponse getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapUserToResponse)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + id));
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email này đã được sử dụng");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ConflictException("Số điện thoại này đã được sử dụng");
        }
        if (request.getCitizenId() != null && userRepository.existsByCitizenId(request.getCitizenId())) {
            throw new ConflictException("Số CCCD này đã tồn tại trên hệ thống");
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));
        
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh"));

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .citizenId(request.getCitizenId())
                .dateOfBirth(request.getDateOfBirth())
                .passwordHash(passwordEncoder.encode(request.getPassword() != null && !request.getPassword().isBlank() ? request.getPassword() : "123456"))
                .status(com.zone.agri.entity.enums.UserStatus.ACTIVE)
                .role(role)
                .branch(branch)
                .provider(com.zone.agri.entity.enums.AuthProvider.LOCAL)
                .build();

        return mapUserToResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));

        if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new ConflictException("Email đã được sử dụng bởi người dùng khác");
        }
        if (userRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), id)) {
            throw new ConflictException("Số điện thoại đã được sử dụng bởi người dùng khác");
        }
        if (request.getCitizenId() != null && userRepository.existsByCitizenIdAndIdNot(request.getCitizenId(), id)) {
            throw new ConflictException("Số CCCD đã được sử dụng bởi người dùng khác");
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));
        
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh"));

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setCitizenId(request.getCitizenId());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setRole(role);
        user.setBranch(branch);
        
        if (request.getStatus() != null) {
            user.setStatus(com.zone.agri.entity.enums.UserStatus.valueOf(request.getStatus().toUpperCase()));
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        return mapUserToResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("Người dùng không tồn tại");
        }
        userRepository.deleteById(id);
    }

    private UserResponse mapUserToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .citizenId(user.getCitizenId())
                .dateOfBirth(user.getDateOfBirth())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roleName(user.getRole() != null ? user.getRole().getDisplayName() : null)
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .branchName(user.getBranch() != null ? user.getBranch().getName() : null)
                .branchId(user.getBranch() != null ? user.getBranch().getId() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

package com.zone.agri.service;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.request.user.ProfileUpdateRequest;
import com.zone.agri.dto.request.user.UserRequest;
import com.zone.agri.dto.response.user.UserResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;

    // ==============================================================
    // QUẢN LÝ PROFILE CÁ NHÂN (DÀNH CHO USER)
    // ==============================================================

    /**
     * Upload ảnh lên Cloudinary và trả về URL
     */
    public Map<String, String> uploadAvatar(MultipartFile file) {
        try {
            CloudinaryService.UploadResult result = cloudinaryService.upload(file, "avatars");
            return Map.of("imageUrl", result.secureUrl());
        } catch (Exception e) {
            log.error("Lỗi upload avatar: ", e);
            throw new RuntimeException("Không thể tải ảnh lên: " + e.getMessage());
        }
    }

    /**
     * Cập nhật thông tin và trả về dữ liệu mới để Frontend đồng bộ UI
     */
    @Transactional
    public UserResponse updateMyProfile(String contact, ProfileUpdateRequest request) {
        User user = userRepository.findByEmail(contact)
                .or(() -> userRepository.findByPhoneNumber(contact))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));

        // ✅ THÊM: Kiểm tra trùng lặp số điện thoại
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            // Nếu SĐT mới đã có người khác dùng (khác ID của user hiện tại)
            if (userRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), user.getId())) {
                throw new ConflictException("Số điện thoại này đã được sử dụng bởi một tài khoản khác!");
            }
        }

        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setDateOfBirth(request.getDateOfBirth());

        if (request.getGender() != null) {
            try {
                user.setGender(Gender.valueOf(request.getGender().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Giới tính không hợp lệ: {}", request.getGender());
            }
        }

        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User savedUser = userRepository.save(user);
        log.info("User {} cập nhật profile thành công", contact);

        return mapUserToResponse(savedUser); // Trả về DTO mới nhất
    }

    /**
     * ✅ THÊM MỚI: Đổi mật khẩu cá nhân
     */
    @Transactional
    public void changePassword(String contact, com.zone.agri.dto.request.user.ChangePasswordRequest request) {
        User user = userRepository.findByEmail(contact)
                .or(() -> userRepository.findByPhoneNumber(contact))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng hiện tại"));

        // 1. Kiểm tra mật khẩu cũ có khớp trong Database không
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác!");
        }

        // 2. Kiểm tra mật khẩu mới và xác nhận mật khẩu (Back-up an toàn phía Backend)
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp!");
        }

        // 3. Mã hóa và lưu mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("User {} đã đổi mật khẩu thành công", contact);
    }

    // ==============================================================
    // QUẢN LÝ NHÂN VIÊN (DÀNH CHO ADMIN)
    // ==============================================================

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
                .orElseThrow(() -> new NotFoundException("User ID " + id + " không tồn tại"));
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) throw new ConflictException("Email đã tồn tại");
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) throw new ConflictException("SĐT đã tồn tại");

        Role role = roleRepository.findById(request.getRoleId()).orElseThrow(() -> new NotFoundException("Vai trò không tồn tại"));
        Branch branch = branchRepository.findById(request.getBranchId()).orElseThrow(() -> new NotFoundException("Chi nhánh không tồn tại"));

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .citizenId(request.getCitizenId())
                .dateOfBirth(request.getDateOfBirth())
                .avatarUrl(request.getAvatarUrl()) // Đồng bộ ảnh
                .passwordHash(passwordEncoder.encode(request.getPassword() != null && !request.getPassword().isBlank() ? request.getPassword() : "123456"))
                .status(UserStatus.ACTIVE)
                .role(role)
                .branch(branch)
                .provider(com.zone.agri.entity.enums.AuthProvider.LOCAL)
                .build();

        return mapUserToResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy user"));

        if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) throw new ConflictException("Email trùng");

        // Kiểm tra trùng lặp SĐT khi Admin cập nhật cho nhân viên
        if (userRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), id)) {
            throw new ConflictException("Số điện thoại đã được sử dụng bởi người dùng khác");
        }

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAvatarUrl(request.getAvatarUrl()); // Cập nhật ảnh
        user.setDateOfBirth(request.getDateOfBirth());

        if (request.getStatus() != null) {
            user.setStatus(UserStatus.valueOf(request.getStatus().toUpperCase()));
        }

        return mapUserToResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) throw new NotFoundException("User không tồn tại");
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
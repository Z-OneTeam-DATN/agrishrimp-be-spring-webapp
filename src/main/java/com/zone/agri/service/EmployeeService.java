package com.zone.agri.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.request.employee.EmployeeCreateRequest;
import com.zone.agri.dto.response.citizen.CitizenLookupResponse;
import com.zone.agri.dto.response.employee.EmployeeResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final String DEFAULT_PASSWORD = "123456"; // Mật khẩu mặc định

    @Transactional
    public EmployeeResponse createEmployee(EmployeeCreateRequest request) {
        log.info("Creating new employee with email: {}", request.getEmail());

        // 1. Validate
        validateUniqueFields(null, request.getEmail(), request.getPhoneNumber(), request.getCitizenId());

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh"));
        Role role = resolveAssignableRole(request.getRoleId());

        // 2. Password handling
        String rawPassword = (request.getPassword() != null && !request.getPassword().isBlank())
                ? request.getPassword()
                : DEFAULT_PASSWORD;
        String hashedPassword = passwordEncoder.encode(rawPassword);

        // 3. Map and Save
        User employee = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .citizenId(request.getCitizenId())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .addressDetail(request.getAddressDetail())
                .startDate(request.getStartDate())
                .passwordHash(hashedPassword)
                .avatarUrl(request.getAvatarUrl())
                .status(parseStatus(request.getStatus()))
                .provider(AuthProvider.LOCAL)
                .branch(branch)
                .role(role)
                .build();

        User savedEmployee = userRepository.save(employee);

        // 4. Send Email
        sendEmailSilently(savedEmployee, rawPassword);

        log.info("Employee created successfully with ID: {}", savedEmployee.getId());
        return mapToResponse(savedEmployee);
    }

    private UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return UserStatus.ACTIVE;
        }

        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            return UserStatus.ACTIVE;
        }
    }

    private void sendEmailSilently(User user, String password) {
        try {
            emailService.sendAccountInfo(user.getEmail(), user.getFullName(), password);
            log.info("Đã gửi email cấp tài khoản cho: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Lỗi khi gửi email cho {}", user.getEmail(), e);
        }
    }

    private void validateUniqueFields(Long id, String email, String phone, String citizenId) {
        boolean emailExists = (id == null)
                ? userRepository.existsByEmail(email)
                : userRepository.existsByEmailAndIdNot(email, id);

        if (emailExists) {
            throw new ConflictException("Email này đã được sử dụng trong hệ thống", true);
        }

        boolean phoneExists = (id == null)
                ? userRepository.existsByPhoneNumber(phone)
                : userRepository.existsByPhoneNumberAndIdNot(phone, id);

        if (phoneExists) {
            throw new ConflictException("Số điện thoại này đã được sử dụng trong hệ thống", true);
        }

        if (citizenId == null || citizenId.isBlank()) {
            return;
        }

        boolean citizenIdExists = (id == null)
                ? userRepository.existsByCitizenId(citizenId)
                : userRepository.existsByCitizenIdAndIdNot(citizenId, id);

        if (citizenIdExists) {
            throw new ConflictException("Số CCCD này đã được sử dụng trong hệ thống", true);
        }
    }

    public Page<EmployeeResponse> getEmployees(String keyword, Long branchId, Long roleId, String status,
            Pageable pageable) {
        UserStatus userStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try {
                userStatus = UserStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", status);
            }
        }
        String searchKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return userRepository.findAllWithFilter(searchKeyword, roleId, branchId, userStatus, pageable)
                .map(this::mapToResponse);
    }

    public EmployeeResponse getEmployeeById(Long employeeId) {
        return userRepository.findById(employeeId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long employeeId, EmployeeCreateRequest request) {
        User existingEmployee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        if (existingEmployee.getRole() != null && Boolean.TRUE.equals(existingEmployee.getRole().getIsSystem())) {
            throw new Forbidden("Không thể sửa nhân viên có vai trò hệ thống");
        }

        validateUniqueFields(
                employeeId,
                existingEmployee.getEmail(),
                request.getPhoneNumber(),
                existingEmployee.getCitizenId());

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh"));
        Role role = resolveAssignableRole(request.getRoleId());

        existingEmployee.setFullName(request.getFullName());
        existingEmployee.setPhoneNumber(request.getPhoneNumber());
        existingEmployee.setDateOfBirth(request.getDateOfBirth());
        existingEmployee.setGender(request.getGender());
        existingEmployee.setAddressDetail(request.getAddressDetail());
        existingEmployee.setStartDate(request.getStartDate());
        existingEmployee.setAvatarUrl(request.getAvatarUrl());
        existingEmployee.setStatus(parseStatus(request.getStatus()));
        existingEmployee.setBranch(branch);
        existingEmployee.setRole(role);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existingEmployee.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        return mapToResponse(userRepository.save(existingEmployee));
    }

    private Role resolveAssignableRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));

        if (!canAssignRole(role)) {
            throw new Forbidden("Bạn không có quyền gán vai trò này");
        }

        return role;
    }

    private boolean canAssignRole(Role role) {
        if (role == null) {
            return false;
        }

        boolean isPrivilegedRole = Boolean.TRUE.equals(role.getIsSystem())
                || RoleUtils.isAdminLikeRole(role.getSlug());

        if (!isPrivilegedRole) {
            return true;
        }

        UserDetail currentUser = AuthUtils.getUserDetail();
        String currentRoleSlug = currentUser != null && currentUser.getRole() != null
                ? currentUser.getRole().getSlug()
                : null;

        return RoleUtils.isAdminLikeRole(currentRoleSlug);
    }

    @Transactional
    public void deleteEmployee(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        if (employee.getRole() != null && Boolean.TRUE.equals(employee.getRole().getIsSystem())) {
            throw new Forbidden("Không thể xóa nhân viên có vai trò hệ thống");
        }

        // Toggle status: ACTIVE <-> INACTIVE
        UserStatus currentStatus = employee.getStatus();
        UserStatus newStatus = (currentStatus == UserStatus.ACTIVE) ? UserStatus.INACTIVE : UserStatus.ACTIVE;
        employee.setStatus(newStatus);
        userRepository.save(employee);
    }

    private EmployeeResponse mapToResponse(User user) {
        // Tự động sinh mã nhân viên dựa vào ID (Ví dụ: NV-0012)
        String employeeCode = String.format("NV-%04d", user.getId());

        return EmployeeResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .employeeCode(employeeCode)
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .citizenId(user.getCitizenId())
                .addressDetail(user.getAddressDetail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .dateOfBirth(user.getDateOfBirth())
                .startDate(user.getStartDate())
                .createdAt(user.getCreatedAt())
                .branch(user.getBranch() != null ? EmployeeResponse.BranchInfo.builder()
                        .id(user.getBranch().getId())
                        .name(user.getBranch().getName())
                        .code(user.getBranch().getBranchCode())
                        .build() : null)
                .role(user.getRole() != null ? EmployeeResponse.RoleInfo.builder()
                        .id(user.getRole().getId())
                        .displayName(user.getRole().getDisplayName())
                        .slug(user.getRole().getSlug())
                        .build() : null)
                .build();
    }

    public CitizenLookupResponse lookupByCitizenId(String citizenId) {
        User user = userRepository.findByCitizenId(citizenId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin CCCD này trong hệ thống"));

        return CitizenLookupResponse.builder()
                .fullName(user.getFullName())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .address(user.getAddressDetail())
                .build();
    }
}

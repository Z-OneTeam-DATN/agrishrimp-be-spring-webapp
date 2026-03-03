package com.zone.agri.service;

import com.zone.agri.dto.request.EmployeeCreateRequest;
import com.zone.agri.dto.response.EmployeeResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.Forbidden;
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
        validateUniqueFields(request.getEmail(), request.getPhoneNumber()); // Đã sửa thành getPhoneNumber()

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh"));
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));

        // 2. Mã hóa mật khẩu (Lấy từ FE gửi lên, nếu trống thì lấy DEFAULT)
        String rawPassword = (request.getPassword() != null && !request.getPassword().isBlank())
                ? request.getPassword()
                : DEFAULT_PASSWORD;
        String hashedPassword = passwordEncoder.encode(rawPassword);

        UserStatus status = "active".equalsIgnoreCase(request.getStatus()) ? UserStatus.ACTIVE : UserStatus.INACTIVE;

        // 3. Build Entity (Ánh xạ thêm CCCD, Ngày sinh, Giới tính)
        User employee = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .citizenId(request.getCitizenId())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .passwordHash(hashedPassword)
                .avatarUrl(request.getAvatarUrl())
                .status(status)
                .branch(branch)
                .role(role)
                .build();

        // 4. Lưu User vào DB (Vì DB của Huy chưa thiết kế cột employeeCode nên ta không gọi hàm set nữa)
        User savedEmployee = userRepository.save(employee);

        // 5. Gửi Email thông báo (Gọi đúng tên hàm sendAccountInfo trong EmailService)
        try {
            emailService.sendAccountInfo(savedEmployee.getEmail(), savedEmployee.getFullName(), rawPassword);
            log.info("Đã gửi email cấp tài khoản cho: {}", savedEmployee.getEmail());
        } catch (Exception e) {
            log.error("Lỗi khi gửi email: ", e);
            // Không throw exception ở đây để không làm rollback việc tạo user nếu mail bị lỗi mạng
        }

        log.info("Employee created successfully with ID: {}", savedEmployee.getId());
        return mapToResponse(savedEmployee);
    }

    private void validateUniqueFields(String email, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email này đã được sử dụng trong hệ thống");
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ConflictException("Số điện thoại này đã được sử dụng trong hệ thống");
        }
    }

    public Page<EmployeeResponse> getEmployees(String keyword, Long branchId, Long roleId, String status, Pageable pageable) {
        UserStatus userStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try {
                userStatus = UserStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", status);
            }
        }
        String searchKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<User> users = userRepository.findAllWithFilter(searchKeyword, roleId, branchId, userStatus, pageable);
        return users.map(this::mapToResponse);
    }

    public EmployeeResponse getEmployeeById(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));
        return mapToResponse(employee);
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long employeeId, EmployeeCreateRequest request) {
        User existingEmployee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        validateUniqueFieldsForUpdate(employeeId, request.getEmail(), request.getPhoneNumber());

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh"));
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));

        UserStatus status = "active".equalsIgnoreCase(request.getStatus()) ? UserStatus.ACTIVE : UserStatus.INACTIVE;

        existingEmployee.setFullName(request.getFullName());
        existingEmployee.setEmail(request.getEmail());
        existingEmployee.setPhoneNumber(request.getPhoneNumber());
        existingEmployee.setCitizenId(request.getCitizenId());
        existingEmployee.setDateOfBirth(request.getDateOfBirth());
        existingEmployee.setGender(request.getGender());
        existingEmployee.setAvatarUrl(request.getAvatarUrl());
        existingEmployee.setStatus(status);
        existingEmployee.setBranch(branch);
        existingEmployee.setRole(role);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existingEmployee.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        User updatedEmployee = userRepository.save(existingEmployee);
        return mapToResponse(updatedEmployee);
    }

    @Transactional
    public void deleteEmployee(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        if (employee.getRole() != null && Boolean.TRUE.equals(employee.getRole().getIsSystem())) {
            throw new Forbidden("Không thể xóa nhân viên có vai trò hệ thống");
        }
        employee.setStatus(UserStatus.INACTIVE);
        userRepository.save(employee);
    }

    private void validateUniqueFieldsForUpdate(Long employeeId, String email, String phone) {
        if (userRepository.existsByEmailAndIdNot(email, employeeId)) {
            throw new ConflictException("Email này đã được nhân viên khác sử dụng");
        }
        if (userRepository.existsByPhoneNumberAndIdNot(phone, employeeId)) {
            throw new ConflictException("Số điện thoại này đã được nhân viên khác sử dụng");
        }
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
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .dateOfBirth(user.getDateOfBirth())
                .startDate(null)
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
                .createdAt(user.getCreatedAt())
                .build();
    }
}
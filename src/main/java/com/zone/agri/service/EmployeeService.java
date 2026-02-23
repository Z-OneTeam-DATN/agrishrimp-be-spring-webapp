package com.zone.agri.service;

import com.zone.agri.dto.request.EmployeeCreateRequest;
import com.zone.agri.dto.response.EmployeeResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.exception.BadRequestException;
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

import java.time.LocalDate;

/**
 * Service for employee management operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_PASSWORD = "Agri@2024";

    /**
     * Create a new employee
     *
     * @param request Employee creation request
     * @return Created employee response
     */
    @Transactional
    public EmployeeResponse createEmployee(EmployeeCreateRequest request) {
        log.info("Creating new employee with email: {}", request.getEmail());

        // 1. Validate unique constraints
        validateUniqueFields(request.getEmail(), request.getPhone());

        // 2. Validate and fetch branch
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        // 3. Validate and fetch role
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò với ID: " + request.getRoleId()));

        // 4. Determine password
        String password = (request.getDefaultPassword() != null && !request.getDefaultPassword().isBlank())
                ? request.getDefaultPassword()
                : DEFAULT_PASSWORD;

        // 5. Hash password
        String hashedPassword = passwordEncoder.encode(password);

        // 6. Map status
        UserStatus status = "active".equalsIgnoreCase(request.getStatus())
                ? UserStatus.ACTIVE
                : UserStatus.INACTIVE;

        // 7. Build User entity
        User employee = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhone())
                .passwordHash(hashedPassword)
                .avatarUrl(request.getAvatarUrl())
                .status(status)
                .branch(branch)
                .role(role)
                .build();

        // Note: startDate is not a field in User entity based on the schema we saw
        // If you need to track startDate, consider adding it to the User entity or storing in metadata

        // 8. Save employee
        User savedEmployee = userRepository.save(employee);
        log.info("Employee created successfully with ID: {}", savedEmployee.getId());

        // 9. Map to response
        return mapToResponse(savedEmployee);
    }

    /**
     * Validate unique constraints for email and phone
     */
    private void validateUniqueFields(String email, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email này đã được sử dụng trong hệ thống");
        }

        if (userRepository.existsByPhoneNumber(phone)) {
            throw new ConflictException("Số điện thoại này đã được sử dụng trong hệ thống");
        }
    }

    /**
     * Get paginated employee list with filters
     *
     * @param keyword Search by name, email, phone
     * @param branchId Filter by branch
     * @param roleId Filter by role
     * @param status Filter by status (active, inactive, etc.)
     * @param pageable Pagination info
     * @return Page of EmployeeResponse
     */
    public Page<EmployeeResponse> getEmployees(
            String keyword,
            Long branchId,
            Long roleId,
            String status,
            Pageable pageable) {

        log.info("Fetching employees with filters - keyword: {}, branchId: {}, roleId: {}, status: {}",
                keyword, branchId, roleId, status);

        // Map status string to enum
        UserStatus userStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try {
                userStatus = UserStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", status);
            }
        }

        String searchKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<User> users = userRepository.findAllWithFilter(
                searchKeyword,
                roleId,
                branchId,
                userStatus,
                pageable
        );

        return users.map(this::mapToResponse);
    }

    /**
     * Get employee by ID
     *
     * @param employeeId Employee ID
     * @return EmployeeResponse
     */
    public EmployeeResponse getEmployeeById(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        return mapToResponse(employee);
    }

    /**
     * Update employee information
     *
     * @param employeeId Employee ID to update
     * @param request Updated employee data
     * @return Updated employee response
     */
    @Transactional
    public EmployeeResponse updateEmployee(Long employeeId, EmployeeCreateRequest request) {
        log.info("Updating employee with ID: {}", employeeId);

        // 1. Find existing employee
        User existingEmployee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        // 2. Validate unique constraints (excluding current employee)
        validateUniqueFieldsForUpdate(employeeId, request.getEmail(), request.getPhone());

        // 3. Validate and fetch branch
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        // 4. Validate and fetch role
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò với ID: " + request.getRoleId()));

        // 5. Map status
        UserStatus status = "active".equalsIgnoreCase(request.getStatus())
                ? UserStatus.ACTIVE
                : UserStatus.INACTIVE;

        // 6. Update fields
        existingEmployee.setFullName(request.getFullName());
        existingEmployee.setEmail(request.getEmail());
        existingEmployee.setPhoneNumber(request.getPhone());
        existingEmployee.setAvatarUrl(request.getAvatarUrl());
        existingEmployee.setStatus(status);
        existingEmployee.setBranch(branch);
        existingEmployee.setRole(role);

        // 7. Update password if provided
        if (request.getDefaultPassword() != null && !request.getDefaultPassword().isBlank()) {
            String hashedPassword = passwordEncoder.encode(request.getDefaultPassword());
            existingEmployee.setPasswordHash(hashedPassword);
        }

        // 8. Save
        User updatedEmployee = userRepository.save(existingEmployee);
        log.info("Employee updated successfully: {}", updatedEmployee.getId());

        return mapToResponse(updatedEmployee);
    }

    /**
     * Delete employee (soft delete by setting status to INACTIVE or hard delete)
     *
     * @param employeeId Employee ID to delete
     */
    @Transactional
    public void deleteEmployee(Long employeeId) {
        log.info("Deleting employee with ID: {}", employeeId);

        // 1. Find employee
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        // 2. Business rule: Cannot delete admin or system users
        if (employee.getRole() != null && Boolean.TRUE.equals(employee.getRole().getIsSystem())) {
            throw new Forbidden("Không thể xóa nhân viên có vai trò hệ thống");
        }

        // 3. Check if employee has related data (orders, transactions, etc.)
        // For now, we'll do soft delete by setting status to INACTIVE
        // If you want hard delete, use: userRepository.delete(employee);

        employee.setStatus(UserStatus.INACTIVE);
        userRepository.save(employee);

        log.info("Employee soft deleted (set to INACTIVE): {}", employeeId);

        // For hard delete, uncomment:
        // userRepository.delete(employee);
        // log.info("Employee hard deleted: {}", employeeId);
    }

    /**
     * Validate unique constraints for update (exclude current employee)
     */
    private void validateUniqueFieldsForUpdate(Long employeeId, String email, String phone) {
        if (userRepository.existsByEmailAndIdNot(email, employeeId)) {
            throw new ConflictException("Email này đã được nhân viên khác sử dụng");
        }

        if (userRepository.existsByPhoneNumberAndIdNot(phone, employeeId)) {
            throw new ConflictException("Số điện thoại này đã được nhân viên khác sử dụng");
        }
    }

    /**
     * Map User entity to EmployeeResponse DTO
     */
    private EmployeeResponse mapToResponse(User user) {
        // Generate employee code from ID (e.g., NV-0001)
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
                .startDate(null) // Set if you add startDate field to User entity
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

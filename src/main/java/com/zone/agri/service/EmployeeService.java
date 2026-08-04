package com.zone.agri.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.request.employee.EmployeeCreateRequest;
import com.zone.agri.dto.response.employee.EmployeeResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;

    private static final String DEFAULT_PASSWORD = "123456"; // Mật khẩu mặc định
    private static final Set<String> EMPLOYEE_STATUSES = Set.of(UserStatus.ACTIVE.name(), UserStatus.INACTIVE.name());
    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");

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
        // Nhan vien vua tao chac chan chua co du lieu nao o dau ca — khoi can chay dynamic schema
        // check tren 1 ID chua ton tai truoc do trong bat ky bang nao.
        return mapToResponse(savedEmployee, false);
    }

    private UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return UserStatus.ACTIVE;
        }

        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if (!EMPLOYEE_STATUSES.contains(normalizedStatus)) {
            throw new BadRequestException("Trạng thái tài khoản không hợp lệ. Chỉ hỗ trợ ACTIVE hoặc INACTIVE.");
        }

        return UserStatus.valueOf(normalizedStatus);
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

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getEmployees(String keyword, Long branchId, Long roleId, String permissionCode, String status,
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
        String normalizedPermissionCode = permissionCode != null && !permissionCode.isBlank()
                ? permissionCode.trim().toUpperCase()
                : null;
        Page<User> employeePage = userRepository.findAllEmployeesWithFilter(
                searchKeyword, roleId, branchId, normalizedPermissionCode, userStatus, pageable);

        // Kiem tra "co du lieu phat sinh" theo LO cho ca trang thay vi tung nhan vien mot — dua
        // N nhan vien x M bang candidate ve chi con M truy van cho ca trang, khong phai N x M.
        List<Long> pageEmployeeIds = employeePage.getContent().stream().map(User::getId).toList();
        Set<Long> employeeIdsWithData = findEmployeeIdsWithGeneratedData(pageEmployeeIds);

        return employeePage.map(user -> mapToResponse(user, employeeIdsWithData.contains(user.getId())));
    }

    @Transactional
    public String resendEmployeeCredentials(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new BadRequestException("Nhân viên này chưa có email để gửi lại thông tin tài khoản");
        }

        String defaultPassword = (employee.getPhoneNumber() != null && !employee.getPhoneNumber().isBlank())
                ? employee.getPhoneNumber().replaceAll("\\s+", "")
                : "123456";

        boolean isAlreadyDefault = passwordEncoder.matches(defaultPassword, employee.getPasswordHash());

        if (!isAlreadyDefault) {
            employee.setPasswordHash(passwordEncoder.encode(defaultPassword));
            userRepository.save(employee);
            log.info("Nhan vien {} da doi mat khau. Set lai ve mat khau mac dinh de gui email.", employee.getEmail());
        } else {
            log.info("Nhan vien {} chua doi mat khau (van dung mat khau mac dinh). Chi gui lai email.", employee.getEmail());
        }

        try {
            emailService.sendAccountInfo(employee.getEmail(), employee.getFullName(), defaultPassword);
            return "Đặt lại mật khẩu và gửi email thành công!";
        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông tin tài khoản cho nhân viên {}: {}", employee.getEmail(), e.getMessage());
            return "Đặt lại mật khẩu thành công! Tuy nhiên không gửi được email do lỗi máy chủ gửi thư. Mật khẩu mặc định là: " + defaultPassword;
        }
    }

    @Transactional(readOnly = true)
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
        User employee = getMutableEmployee(employeeId);

        // Toggle status: ACTIVE <-> INACTIVE
        UserStatus currentStatus = employee.getStatus();
        UserStatus newStatus = (currentStatus == UserStatus.ACTIVE) ? UserStatus.INACTIVE : UserStatus.ACTIVE;
        employee.setStatus(newStatus);
        userRepository.save(employee);
    }

    @Transactional
    public void updateEmployeeStatus(Long employeeId, String status) {
        User employee = getMutableEmployee(employeeId);
        employee.setStatus(parseStatus(status));
        userRepository.save(employee);
    }

    @Transactional
    public void permanentlyDeleteEmployee(Long employeeId) {
        User employee = getMutableEmployee(employeeId);

        Map<String, Long> blockingReferences = findBlockingReferences(employeeId);
        if (!blockingReferences.isEmpty()) {
            throw new BadRequestException(buildDeleteBlockedMessage(blockingReferences));
        }

        userRepository.delete(employee);
    }

    private EmployeeResponse mapToResponse(User user) {
        return mapToResponse(user, hasGeneratedDataSafe(user.getId()));
    }

    private EmployeeResponse mapToResponse(User user, boolean hasGeneratedData) {
        // Tự động sinh mã nhân viên dựa vào ID (Ví dụ: NV-0012)
        String employeeCode = String.format("NV-%04d", user.getId());
        boolean isSystemAccount = user.getRole() != null && Boolean.TRUE.equals(user.getRole().getIsSystem());

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
                .isSystemAccount(isSystemAccount)
                .hasGeneratedData(hasGeneratedData)
                .build();
    }

    /**
     * Ban khong throw cua findBlockingReferences — dung de HIEN THI (an/hien nut xoa), khong phai
     * de chan hanh dong. Neu kiem tra loi (vd truc trac schema), fail CLOSED: coi nhu co du lieu
     * de khong lo hien nham nut xoa cho 1 nhan vien thuc ra khong xoa duoc.
     */
    private boolean hasGeneratedDataSafe(Long employeeId) {
        try {
            return !findBlockingReferences(employeeId).isEmpty();
        } catch (Exception ex) {
            return true;
        }
    }

    /**
     * Ban theo LO cua findBlockingReferences — dung cho danh sach nhan vien (getEmployees) de
     * tranh chay N x M truy van (N nhan vien, M bang candidate) khi chi can hien/an nut xoa. Gop
     * lai thanh M truy van GROUP theo ca trang, bat ke trang co bao nhieu nhan vien.
     */
    private Set<Long> findEmployeeIdsWithGeneratedData(List<Long> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Set.of();
        }

        try {
            String schemaName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (schemaName == null || schemaName.isBlank()) {
                throw new IllegalStateException("Database schema is empty");
            }

            List<TableColumnRef> candidates = new ArrayList<>();
            candidates.addAll(loadForeignKeyReferences(schemaName));
            candidates.addAll(loadAuditAndLegacyReferences(schemaName));

            Set<Long> employeeIdsWithData = new HashSet<>();
            String placeholders = employeeIds.stream().map(id -> "?").collect(Collectors.joining(","));

            for (TableColumnRef reference : candidates) {
                if (employeeIdsWithData.size() == employeeIds.size()) {
                    break;
                }
                if (!isSafeIdentifier(reference.tableName()) || !isSafeIdentifier(reference.columnName())) {
                    continue;
                }

                List<Long> matchedIds = jdbcTemplate.queryForList(
                        "SELECT DISTINCT `" + reference.columnName() + "` FROM `" + reference.tableName()
                                + "` WHERE `" + reference.columnName() + "` IN (" + placeholders + ")",
                        Long.class,
                        employeeIds.toArray());
                employeeIdsWithData.addAll(matchedIds);
            }

            return employeeIdsWithData;
        } catch (Exception ex) {
            log.error("Khong the kiem tra hang loat du lieu phat sinh cho danh sach nhan vien", ex);
            return new HashSet<>(employeeIds);
        }
    }

    private User getMutableEmployee(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        if (employee.getRole() != null && Boolean.TRUE.equals(employee.getRole().getIsSystem())) {
            throw new Forbidden("Không thể thao tác với nhân viên có vai trò hệ thống");
        }

        return employee;
    }

    private Map<String, Long> findBlockingReferences(Long employeeId) {
        try {
            String schemaName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (schemaName == null || schemaName.isBlank()) {
                throw new IllegalStateException("Database schema is empty");
            }

            List<TableColumnRef> candidates = new ArrayList<>();
            candidates.addAll(loadForeignKeyReferences(schemaName));
            candidates.addAll(loadAuditAndLegacyReferences(schemaName));

            Map<String, Long> referencesByTable = new LinkedHashMap<>();
            for (TableColumnRef reference : candidates) {
                if (!isSafeIdentifier(reference.tableName()) || !isSafeIdentifier(reference.columnName())) {
                    continue;
                }

                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM `" + reference.tableName() + "` WHERE `" + reference.columnName() + "` = ?",
                        Long.class,
                        employeeId);

                if (count != null && count > 0) {
                    referencesByTable.merge(reference.tableName(), count, Long::sum);
                }
            }

            return referencesByTable;
        } catch (Exception ex) {
            log.error("Khong the kiem tra phat sinh du lieu cho nhan vien {}", employeeId, ex);
            throw new BadRequestException(
                    "Không thể xác minh dữ liệu phát sinh của nhân viên này. Vui lòng thử lại sau hoặc dùng tạm khóa.");
        }
    }

    private List<TableColumnRef> loadForeignKeyReferences(String schemaName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT DISTINCT table_name, column_name
                FROM information_schema.key_column_usage
                WHERE table_schema = ?
                  AND referenced_table_name = 'users'
                  AND referenced_column_name = 'id'
                  AND table_name <> 'users'
                """, schemaName);

        return mapToReferences(rows);
    }

    private List<TableColumnRef> loadAuditAndLegacyReferences(String schemaName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT DISTINCT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name <> 'users'
                  AND (
                    column_name IN ('created_by_user_id', 'updated_by_user_id', 'sender_id', 'receiver_id', 'assigned_staff_id', 'staff_assigned_id', 'author_id')
                    OR column_name = 'user_id'
                    OR column_name LIKE '%\\_user_id' ESCAPE '\\'
                    OR column_name LIKE '%\\_staff_id' ESCAPE '\\'
                  )
                """, schemaName);

        return mapToReferences(rows);
    }

    private List<TableColumnRef> mapToReferences(List<Map<String, Object>> rows) {
        Map<String, TableColumnRef> uniqueReferences = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String tableName = String.valueOf(row.get("table_name"));
            String columnName = String.valueOf(row.get("column_name"));
            uniqueReferences.putIfAbsent(tableName + "." + columnName, new TableColumnRef(tableName, columnName));
        }
        return new ArrayList<>(uniqueReferences.values());
    }

    private boolean isSafeIdentifier(String identifier) {
        return identifier != null && SAFE_SQL_IDENTIFIER.matcher(identifier).matches();
    }

    private String buildDeleteBlockedMessage(Map<String, Long> references) {
        List<String> samples = references.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .limit(5)
                .map(entry -> humanizeTableName(entry.getKey()) + " (" + entry.getValue() + ")")
                .toList();

        return "Không thể xóa nhân viên này vì tài khoản đã phát sinh dữ liệu trong hệ thống: "
                + String.join(", ", samples)
                + ". Vui lòng dùng tạm khóa thay vì xóa.";
    }

    private String humanizeTableName(String tableName) {
        return switch (tableName) {
            case "blog_posts" -> "bài viết";
            case "cart_items" -> "giỏ hàng";
            case "chat_messages" -> "tin nhắn";
            case "conversations" -> "hội thoại";
            case "customers" -> "khách hàng phụ trách";
            case "inventory_transfers" -> "phiếu điều chuyển";
            case "mini_app_diagnosis_history", "mini_app_diagnosis_histories" -> "lịch sử chẩn đoán";
            case "notifications" -> "thông báo";
            case "orders" -> "đơn hàng";
            case "push_subscriptions" -> "thiết bị nhận thông báo";
            case "reviews" -> "đánh giá";
            case "supplier_product_catalog" -> "catalog nhà cung cấp";
            case "suppliers" -> "nhà cung cấp";
            case "user_addresses" -> "địa chỉ người dùng";
            case "user_vouchers" -> "voucher người dùng";
            default -> tableName.replace('_', ' ');
        };
    }

    private record TableColumnRef(String tableName, String columnName) {
    }
}

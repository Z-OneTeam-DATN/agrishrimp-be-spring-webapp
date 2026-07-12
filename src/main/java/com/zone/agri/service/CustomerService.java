package com.zone.agri.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.request.customer.CustomerInternalNoteRequest;
import com.zone.agri.dto.request.customer.CustomerRequest;
import com.zone.agri.dto.response.customer.CustomerAddressResponse;
import com.zone.agri.dto.response.customer.CustomerDetailResponse;
import com.zone.agri.dto.response.customer.CustomerInternalNoteResponse;
import com.zone.agri.dto.response.customer.CustomerResponse;
import com.zone.agri.dto.response.customer.CustomerStatusLogResponse;
import com.zone.agri.entity.Customer;
import com.zone.agri.entity.CustomerInternalNote;
import com.zone.agri.entity.CustomerStatusLog;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.UserAddress;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.CustomerStatus;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.CustomerInternalNoteRepository;
import com.zone.agri.repository.CustomerRepository;
import com.zone.agri.repository.CustomerStatusLogRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserAddressRepository;
import com.zone.agri.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private static final Set<String> MANAGED_CUSTOMER_ROLE_SLUGS = Set.of("USER");
    private static final long MIN_SETTLED_ORDERS_FOR_RISK_ASSESSMENT = 3L;
    private static final double LOW_RISK_REPUTATION_THRESHOLD = 80.0;
    private static final double HIGH_RISK_REPUTATION_THRESHOLD = 50.0;

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserAddressRepository userAddressRepository;
    private final OrderRepository orderRepository;
    private final CustomerInternalNoteRepository customerInternalNoteRepository;
    private final CustomerStatusLogRepository customerStatusLogRepository;
    private final BranchRepository branchRepository;

    private record CustomerRiskAssessment(long settledOrders, double reputationScore, String riskLevel,
            boolean onlinePaymentOnly) {
        private boolean hasEnoughData() {
            return settledOrders >= MIN_SETTLED_ORDERS_FOR_RISK_ASSESSMENT;
        }
    }

    @Transactional
    public Customer createCustomer(CustomerRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ConflictException("Email " + req.getEmail() + " da co tai khoan trong he thong!", true);
        }
        if (userRepository.existsByPhoneNumber(req.getPhone())) {
            throw new ConflictException("SDT " + req.getPhone() + " da duoc su dung!", true);
        }

        String randomPassword = (req.getPhone() != null && !req.getPhone().isBlank())
                ? req.getPhone().replaceAll("\\s+", "")
                : "123456";

        Role customerRole = roleRepository.findBySlug("USER")
                .orElseThrow(() -> new NotFoundException("Role USER chua duoc cau hinh"));

        User newUser = User.builder()
                .fullName(req.getName())
                .email(req.getEmail())
                .phoneNumber(req.getPhone())
                .passwordHash(passwordEncoder.encode(randomPassword))
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .role(customerRole)
                .build();

        Customer customer = new Customer();
        mapRequestToEntity(req, customer);
        customer.setUser(newUser);

        if (customer.getStatus() == null) {
            customer.setStatus(CustomerStatus.ACTIVE);
        }

        Customer savedCustomer = customerRepository.save(customer);
        if (req.getAddressDetail() != null && !req.getAddressDetail().isEmpty()) {
            UserAddress defaultAddress = UserAddress.builder()
                    .user(newUser)
                    .receiverName(req.getName())
                    .receiverPhone(req.getPhone())
                    .addressDetail(req.getAddressDetail())
                    .provinceId(req.getProvinceId())
                    .districtId(req.getDistrictId())
                    .wardId(req.getWardId())
                    .isDefault(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            userAddressRepository.save(defaultAddress);
        }

        try {
            emailService.sendAccountInfo(req.getEmail(), req.getName(), randomPassword);
        } catch (Exception e) {
            log.error("Khong gui duoc email thong bao tai khoan cho {}: {}", req.getEmail(), e.getMessage());
        }

        return savedCustomer;
    }

    @Transactional
    public Customer updateCustomer(Long id, CustomerRequest req) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay khach hang"));

        if (!customer.getPhone().equals(req.getPhone()) && customerRepository.existsByPhone(req.getPhone())) {
            throw new ConflictException("So dien thoai moi da duoc su dung boi nguoi khac!", true);
        }

        mapRequestToEntity(req, customer);
        return customerRepository.save(customer);
    }

    public Page<CustomerResponse> getCustomers(String keyword, String statusStr, Pageable pageable) {
        String finalStatus = (statusStr == null || statusStr.trim().isEmpty()) ? "all" : statusStr.trim();
        String normalizedPhoneKeyword = keyword == null ? null : keyword.replaceAll("\\D+", "");
        if (normalizedPhoneKeyword != null && normalizedPhoneKeyword.isBlank()) {
            normalizedPhoneKeyword = null;
        }

        Long branchScopeId = resolveCustomerScopeBranchId();
        UserStatus userStatus = null;
        if (!"all".equalsIgnoreCase(finalStatus)) {
            try {
                userStatus = UserStatus.valueOf(finalStatus.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Trang thai khach hang khong hop le: " + finalStatus);
            }
        }

        Page<User> users = userRepository.searchCustomerUsers(
                keyword,
                normalizedPhoneKeyword,
                userStatus,
                branchScopeId,
                pageable);

        return users.map(this::convertToResponse);
    }

    public Map<String, Boolean> checkDuplicate(String email, String phone) {
        Map<String, Boolean> result = new HashMap<>();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String normalizedPhone = phone == null ? "" : phone.replaceAll("\\D+", "");

        result.put("emailExists", !normalizedEmail.isEmpty() && userRepository.existsByEmail(normalizedEmail));
        result.put("phoneExists", !normalizedPhone.isEmpty() && userRepository.existsByPhoneNumber(normalizedPhone));
        return result;
    }

    private CustomerResponse convertToResponse(User user) {
        Long userId = user.getId();
        Customer customer = user.getCustomer();

        CustomerResponse dto = new CustomerResponse();
        dto.setUserId(userId);
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setProvider(user.getProvider());
        dto.setUserStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setAvatarUrl(user.getAvatarUrl());

        List<UserAddress> defaultAddresses = Optional
                .ofNullable(userAddressRepository.findByUserIdAndIsDefaultTrue(userId))
                .orElse(Collections.emptyList());
        if (!defaultAddresses.isEmpty()) {
            UserAddress defaultAddr = defaultAddresses.get(0);
            dto.setAddressDetail(defaultAddr.getAddressDetail());
            dto.setPhone(
                    defaultAddr.getReceiverPhone() != null ? defaultAddr.getReceiverPhone() : user.getPhoneNumber());
        } else if (customer != null) {
            dto.setAddressDetail(customer.getAddressDetail());
            dto.setPhone(customer.getPhone() != null ? customer.getPhone() : user.getPhoneNumber());
        } else {
            dto.setPhone(user.getPhoneNumber());
        }

        if (customer != null) {
            dto.setCustomerId(customer.getId());
            dto.setCustomerStatus(customer.getStatus());
        }

        Long totalOrders = Optional.ofNullable(orderRepository.countTotalOrdersByUserId(userId)).orElse(0L);
        BigDecimal totalSpent = Optional.ofNullable(orderRepository.sumTotalSpentByUserId(userId))
                .orElse(BigDecimal.ZERO);
        CustomerRiskAssessment riskAssessment = assessCustomerRisk(userId);

        dto.setTotalOrders(totalOrders);
        dto.setTotalSpent(totalSpent);
        dto.setReputationScore(riskAssessment.reputationScore());
        dto.setRiskLevel(riskAssessment.riskLevel());
        dto.setOnlinePaymentOnly(riskAssessment.onlinePaymentOnly());

        return dto;
    }

    private Long resolveCustomerScopeBranchId() {
        var currentUser = AuthUtils.getUserDetail();
        if (currentUser == null || currentUser.getRole() == null) {
            return null;
        }

        if (Boolean.TRUE.equals(currentUser.getRole().getIsSystem())) {
            return null;
        }

        String roleSlug = currentUser.getRole().getSlug();
        if (RoleUtils.isAdminLikeRole(roleSlug)) {
            return null;
        }

        if (currentUser.getBranchId() == null) {
            throw new AccessDeniedException("Tai khoan nay chua duoc gan chi nhanh.");
        }

        return currentUser.getBranchId();
    }

    private Optional<User> resolveManagedCustomerUser(Long identifier) {
        Optional<User> userMatch = userRepository.findById(identifier)
                .filter(this::isManagedCustomerUser);
        if (userMatch.isPresent()) {
            return userMatch;
        }

        return customerRepository.findById(identifier)
                .map(Customer::getUser)
                .filter(this::isManagedCustomerUser);
    }

    private boolean isManagedCustomerUser(User user) {
        return user != null
                && user.getRole() != null
                && MANAGED_CUSTOMER_ROLE_SLUGS.contains(user.getRole().getSlug());
    }

    private void validateCustomerAccess(User customerUser) {
        Long branchScopeId = resolveCustomerScopeBranchId();
        if (branchScopeId == null) {
            return;
        }

        Customer customer = customerUser.getCustomer();
        Long customerBranchId = customer != null && customer.getAssignedBranch() != null
                ? customer.getAssignedBranch().getId()
                : null;
        if (customerBranchId == null || !branchScopeId.equals(customerBranchId)) {
            throw new AccessDeniedException("Ban khong co quyen xem khach hang ngoai chi nhanh cua minh.");
        }
    }

    private User getAccessibleCustomerUser(Long identifier) {
        User customerUser = resolveManagedCustomerUser(identifier)
                .orElseThrow(() -> new NotFoundException("Khong tim thay khach hang"));
        validateCustomerAccess(customerUser);
        return customerUser;
    }

    private CustomerRiskAssessment assessCustomerRisk(Long userId) {
        long settledOrders = Optional.ofNullable(orderRepository.countSettledOrdersByUserId(userId)).orElse(0L);
        long completedOrders = Optional.ofNullable(orderRepository.countCompletedOrdersByUserId(userId)).orElse(0L);

        double reputationScore = 0.0;
        if (settledOrders > 0) {
            double score = (double) completedOrders / settledOrders * 100;
            reputationScore = Math.round(score * 100.0) / 100.0;
        }

        boolean hasEnoughData = settledOrders >= MIN_SETTLED_ORDERS_FOR_RISK_ASSESSMENT;
        return new CustomerRiskAssessment(
                settledOrders,
                reputationScore,
                hasEnoughData ? determineRiskLevel(reputationScore) : "UNKNOWN",
                hasEnoughData && requiresOnlinePayment(reputationScore));
    }

    public boolean requiresOnlinePayment(Long userId) {
        User user = resolveManagedCustomerUser(userId).orElse(null);
        if (user == null || user.getRole() == null || user.getRole().getSlug().equals("ADMIN")) {
            return false;
        }

        CustomerRiskAssessment riskAssessment = assessCustomerRisk(user.getId());
        if (!riskAssessment.hasEnoughData()) {
            return false;
        }

        return riskAssessment.onlinePaymentOnly();
    }

    private boolean requiresOnlinePayment(double reputationScore) {
        return reputationScore < HIGH_RISK_REPUTATION_THRESHOLD;
    }

    private String determineRiskLevel(double reputationScore) {
        if (reputationScore >= LOW_RISK_REPUTATION_THRESHOLD) {
            return "LOW";
        }
        if (reputationScore >= HIGH_RISK_REPUTATION_THRESHOLD) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Khong tim thay tai khoan nguoi dung"));

        UserStatus fromStatus = user.getStatus();
        user.setStatus(user.getStatus() == UserStatus.ACTIVE ? UserStatus.INACTIVE : UserStatus.ACTIVE);
        userRepository.save(user);
        saveStatusLog(user, fromStatus, user.getStatus(), "ADMIN_TOGGLE_STATUS");
    }

    @Transactional
    public String resendCustomerCredentials(Long userId) {
        User customerUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Khong tim thay khach hang"));

        if (customerUser.getEmail() == null || customerUser.getEmail().isBlank()) {
            throw new BadRequestException("Khach hang nay chua co email de nhan thong tin tai khoan");
        }

        String defaultPassword = (customerUser.getPhoneNumber() != null && !customerUser.getPhoneNumber().isBlank())
                ? customerUser.getPhoneNumber().replaceAll("\\s+", "")
                : "123456";

        boolean isAlreadyDefault = passwordEncoder.matches(defaultPassword, customerUser.getPasswordHash());

        if (!isAlreadyDefault) {
            customerUser.setPasswordHash(passwordEncoder.encode(defaultPassword));
            userRepository.save(customerUser);
            log.info("Khach hang {} da doi mat khau. Set lai ve mat khau mac dinh de gui email.", customerUser.getEmail());
        } else {
            log.info("Khach hang {} chua doi mat khau (van dung mat khau mac dinh). Chi gui lai email.", customerUser.getEmail());
        }

        try {
            emailService.sendAccountInfo(customerUser.getEmail(), customerUser.getFullName(), defaultPassword);
            return "Đặt lại mật khẩu và gửi email thành công!";
        } catch (Exception e) {
            log.error("Loi khi gui email thong tin tai khoan cho khach hang {}: {}", customerUser.getEmail(), e.getMessage());
            return "Đặt lại mật khẩu thành công! Tuy nhiên không gửi được email do lỗi máy chủ gửi thư. Mật khẩu mặc định là: " + defaultPassword;
        }
    }

    public CustomerResponse getCustomerById(Long identifier) {
        return convertToResponse(getAccessibleCustomerUser(identifier));
    }

    public CustomerDetailResponse getCustomerDetailById(Long identifier) {
        User customerUser = getAccessibleCustomerUser(identifier);
        Long resolvedUserId = customerUser.getId();
        CustomerResponse base = convertToResponse(customerUser);

        LocalDateTime lastOrderDate = orderRepository.findLastOrderDateByUserId(resolvedUserId);
        Double averageOrderValueRaw = orderRepository.findAverageOrderValueByUserId(resolvedUserId);
        BigDecimal averageOrderValue = averageOrderValueRaw != null ? BigDecimal.valueOf(averageOrderValueRaw)
                : BigDecimal.ZERO;

        List<CustomerAddressResponse> addresses = Optional
                .ofNullable(userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(resolvedUserId))
                .orElse(Collections.emptyList())
                .stream()
                .map(addr -> CustomerAddressResponse.builder()
                        .id(addr.getId())
                        .receiverName(addr.getReceiverName())
                        .receiverPhone(addr.getReceiverPhone())
                        .addressDetail(addr.getAddressDetail())
                        .isDefault(Boolean.TRUE.equals(addr.getIsDefault()))
                        .createdAt(addr.getCreatedAt())
                        .build())
                .toList();

        return CustomerDetailResponse.builder()
                .userId(base.getUserId())
                .fullName(base.getFullName())
                .email(base.getEmail())
                .phone(base.getPhone())
                .avatarUrl(base.getAvatarUrl())
                .provider(base.getProvider())
                .userStatus(base.getUserStatus())
                .createdAt(base.getCreatedAt())
                .customerId(base.getCustomerId())
                .customerStatus(base.getCustomerStatus())
                .addressDetail(base.getAddressDetail())
                .totalOrders(base.getTotalOrders())
                .totalSpent(base.getTotalSpent())
                .reputationScore(base.getReputationScore())
                .riskLevel(base.getRiskLevel())
                .onlinePaymentOnly(base.getOnlinePaymentOnly())
                .lastOrderDate(lastOrderDate)
                .averageOrderValue(averageOrderValue)
                .addresses(addresses)
                .internalNotes(getInternalNotes(resolvedUserId))
                .statusLogs(getStatusLogs(resolvedUserId))
                .build();
    }

    public List<CustomerInternalNoteResponse> getInternalNotes(Long identifier) {
        Long resolvedUserId = getAccessibleCustomerUser(identifier).getId();
        List<CustomerInternalNote> notes = customerInternalNoteRepository
                .findByCustomerUserIdOrderByCreatedAtDesc(resolvedUserId);
        return mapNotes(notes);
    }

    @Transactional
    public CustomerInternalNoteResponse addInternalNote(Long identifier, CustomerInternalNoteRequest request) {
        User customerUser = getAccessibleCustomerUser(identifier);

        CustomerInternalNote note = CustomerInternalNote.builder()
                .customerUser(customerUser)
                .content(request.getContent().trim())
                .build();

        CustomerInternalNote saved = customerInternalNoteRepository.save(note);
        return mapNotes(Collections.singletonList(saved)).get(0);
    }

    @Transactional
    public void deleteInternalNote(Long noteId) {
        if (!customerInternalNoteRepository.existsById(noteId)) {
            throw new NotFoundException("Khong tim thay ghi chu noi bo");
        }
        customerInternalNoteRepository.deleteById(noteId);
    }

    public List<CustomerStatusLogResponse> getStatusLogs(Long identifier) {
        Long resolvedUserId = getAccessibleCustomerUser(identifier).getId();
        List<CustomerStatusLog> logs = customerStatusLogRepository
                .findByCustomerUserIdOrderByCreatedAtDesc(resolvedUserId);
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> userNameById = userRepository.findAllById(
                logs.stream()
                        .map(CustomerStatusLog::getCreatedByUserId)
                        .filter(id -> id != null && id > 0)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return logs.stream()
                .map(statusLog -> CustomerStatusLogResponse.builder()
                        .id(statusLog.getId())
                        .fromStatus(statusLog.getFromStatus())
                        .toStatus(statusLog.getToStatus())
                        .reason(statusLog.getReason())
                        .changedByName(userNameById.getOrDefault(statusLog.getCreatedByUserId(), "He thong"))
                        .createdAt(statusLog.getCreatedAt())
                        .build())
                .toList();
    }

    private List<CustomerInternalNoteResponse> mapNotes(List<CustomerInternalNote> notes) {
        if (notes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> authorNameById = userRepository.findAllById(
                notes.stream()
                        .map(CustomerInternalNote::getCreatedByUserId)
                        .filter(id -> id != null && id > 0)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return notes.stream()
                .map(note -> CustomerInternalNoteResponse.builder()
                        .id(note.getId())
                        .content(note.getContent())
                        .authorName(authorNameById.getOrDefault(note.getCreatedByUserId(), "He thong"))
                        .createdAt(note.getCreatedAt())
                        .updatedAt(note.getUpdatedAt())
                        .build())
                .toList();
    }

    private void saveStatusLog(User customerUser, UserStatus fromStatus, UserStatus toStatus, String reason) {
        CustomerStatusLog statusLog = CustomerStatusLog.builder()
                .customerUser(customerUser)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(reason)
                .build();
        customerStatusLogRepository.save(statusLog);
    }

    @Transactional
    public void evaluateAndHandleCustomerReputation(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() == null || user.getRole().getSlug().equals("ADMIN")) {
            return;
        }

        CustomerRiskAssessment riskAssessment = assessCustomerRisk(userId);
        if (!riskAssessment.hasEnoughData()) {
            return;
        }

        if ("HIGH".equals(riskAssessment.riskLevel())) {
            try {
                emailService.sendWarningEmail(user.getEmail(), user.getFullName(), riskAssessment.reputationScore());
                log.info("Da gui canh bao tai khoan user_id {} do uy tin thap ({}%)",
                        userId,
                        riskAssessment.reputationScore());
            } catch (Exception e) {
                log.error("Loi gui mail canh bao: {}", e.getMessage());
            }
        }
    }

    private void mapRequestToEntity(CustomerRequest req, Customer c) {
        c.setName(req.getName());
        c.setPhone(req.getPhone());
        c.setEmail(req.getEmail());
        c.setGender(req.getGender());
        c.setProvinceId(req.getProvinceId());
        c.setDistrictId(req.getDistrictId());
        c.setWardId(req.getWardId());
        c.setAddressDetail(req.getAddressDetail());
        c.setStatus(req.getStatus());
        c.setNote(req.getNote());

        if (req.getBranchId() != null) {
            c.setAssignedBranch(branchRepository.findById(req.getBranchId()).orElse(null));
        }
        if (req.getStaffAssignedId() != null) {
            c.setStaffAssigned(userRepository.findById(req.getStaffAssignedId()).orElse(null));
        }
        if (req.getInternalNotes() != null) {
            c.setInternalNotes(req.getInternalNotes());
        }
    }

    public List<Map<String, Object>> getStaffByBranch(Long branchId) {
        return userRepository.findByBranchIdAndRole(branchId, "MANAGER");
    }

    public List<?> getAllBranches() {
        return branchRepository.findAll();
    }
}

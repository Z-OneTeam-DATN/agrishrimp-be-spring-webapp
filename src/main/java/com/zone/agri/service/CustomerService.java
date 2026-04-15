package com.zone.agri.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // 1. Tạo mới khách hàng
    @Transactional
    public Customer createCustomer(CustomerRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ConflictException("Email " + req.getEmail() + " đã có tài khoản trong hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(req.getPhone())) {
            throw new ConflictException("SĐT " + req.getPhone() + " đã được sử dụng!");
        }

        String randomPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Role customerRole = roleRepository.findBySlug("CUSTOMER")
                .orElseThrow(() -> new NotFoundException("Role CUSTOMER chưa được cấu hình"));

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

        if (customer.getStatus() == null)
            customer.setStatus(CustomerStatus.ACTIVE);

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
            log.error("Không gửi được email thông báo tài khoản cho {}: {}", req.getEmail(), e.getMessage());
        }

        return savedCustomer;
    }

    // 2. Cập nhật khách hàng
    @Transactional
    public Customer updateCustomer(Long id, CustomerRequest req) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng"));

        if (!customer.getPhone().equals(req.getPhone()) && customerRepository.existsByPhone(req.getPhone())) {
            throw new ConflictException("Số điện thoại mới đã được sử dụng bởi người khác!");
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
        UserStatus userStatus = "all".equalsIgnoreCase(finalStatus) ? null : UserStatus.valueOf(finalStatus);

        Page<Customer> customers = customerRepository.searchCustomers(
                keyword,
                normalizedPhoneKeyword,
                branchScopeId,
                userStatus,
                pageable);

        return customers.map(this::convertToResponse);
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

        // Ưu tiên địa chỉ mặc định từ bảng UserAddress
        List<UserAddress> defaultAddresses = userAddressRepository.findByUserIdAndIsDefaultTrue(userId);
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

        // Fetch order statistics
        Long totalOrders = Optional.ofNullable(orderRepository.countTotalOrdersByUserId(userId)).orElse(0L);
        Long settledOrders = Optional.ofNullable(orderRepository.countSettledOrdersByUserId(userId)).orElse(0L);
        Long completedOrders = Optional.ofNullable(orderRepository.countCompletedOrdersByUserId(userId))
                .orElse(0L);
        BigDecimal totalSpent = Optional.ofNullable(orderRepository.sumTotalSpentByUserId(userId))
                .orElse(BigDecimal.ZERO);

        dto.setTotalOrders(totalOrders);
        dto.setTotalSpent(totalSpent);

        double reputationScore = 0.0;
        if (settledOrders > 0) {
            double score = (double) completedOrders / settledOrders * 100;
            reputationScore = Math.round(score * 100.0) / 100.0;
        }

        boolean hasEnoughOrdersForAssessment = settledOrders != null && settledOrders >= 3;

        dto.setReputationScore(reputationScore);
        dto.setRiskLevel(hasEnoughOrdersForAssessment ? determineRiskLevel(reputationScore) : "UNKNOWN");
        dto.setOnlinePaymentOnly(hasEnoughOrdersForAssessment && requiresOnlinePayment(reputationScore));

        return dto;
    }

    private CustomerResponse convertToResponse(Customer customer) {
        return convertToResponse(customer.getUser());
    }

    private Long resolveCustomerScopeBranchId() {
        var currentUser = AuthUtils.getUserDetail();
        if (currentUser == null || currentUser.getRole() == null) {
            return null;
        }

        String roleSlug = currentUser.getRole().getSlug();
        if (RoleUtils.isAdminLikeRole(roleSlug)) {
            return null;
        }

        if (currentUser.getBranchId() == null) {
            throw new AccessDeniedException("Tài khoản này chưa được gán chi nhánh.");
        }

        return currentUser.getBranchId();
    }

    private Customer getAccessibleCustomerByUserId(Long userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng"));

        Long branchScopeId = resolveCustomerScopeBranchId();
        if (branchScopeId != null) {
            Long customerBranchId = customer.getAssignedBranch() != null ? customer.getAssignedBranch().getId() : null;
            if (customerBranchId == null || !branchScopeId.equals(customerBranchId)) {
                throw new AccessDeniedException("Bạn không có quyền xem khách hàng ngoài chi nhánh của mình.");
            }
        }

        return customer;
    }

    public boolean requiresOnlinePayment(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() == null || user.getRole().getSlug().equals("ADMIN")) {
            return false;
        }

        Long settledOrders = orderRepository.countSettledOrdersByUserId(userId);
        Long completedOrders = orderRepository.countCompletedOrdersByUserId(userId);
        if (settledOrders == null || settledOrders < 3) {
            return false;
        }

        double reputationScore = (double) (completedOrders != null ? completedOrders : 0) / settledOrders * 100;
        return requiresOnlinePayment(reputationScore);
    }

    private boolean requiresOnlinePayment(double reputationScore) {
        return reputationScore < 50.0;
    }

    private String determineRiskLevel(double reputationScore) {
        if (reputationScore >= 80.0)
            return "LOW";
        if (reputationScore >= 50.0)
            return "MEDIUM";
        return "HIGH";
    }

    // 5. Khóa / Mở khóa tài khoản
    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản người dùng"));

        UserStatus fromStatus = user.getStatus();
        user.setStatus(user.getStatus() == UserStatus.ACTIVE ? UserStatus.INACTIVE : UserStatus.ACTIVE);
        userRepository.save(user);
        saveStatusLog(user, fromStatus, user.getStatus(), "ADMIN_TOGGLE_STATUS");
    }

    public CustomerResponse getCustomerById(Long userId) {
        return convertToResponse(getAccessibleCustomerByUserId(userId).getUser());
    }

    public CustomerDetailResponse getCustomerDetailById(Long userId) {
        Customer customer = getAccessibleCustomerByUserId(userId);
        CustomerResponse base = convertToResponse(customer.getUser());

        LocalDateTime lastOrderDate = orderRepository.findLastOrderDateByUserId(userId);
        Double averageOrderValueRaw = orderRepository.findAverageOrderValueByUserId(userId);
        BigDecimal averageOrderValue = averageOrderValueRaw != null ? BigDecimal.valueOf(averageOrderValueRaw)
                : BigDecimal.ZERO;

        List<CustomerAddressResponse> addresses = userAddressRepository
                .findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
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
                .internalNotes(getInternalNotes(userId))
                .statusLogs(getStatusLogs(userId))
                .build();
    }

    public List<CustomerInternalNoteResponse> getInternalNotes(Long userId) {
        getAccessibleCustomerByUserId(userId);
        List<CustomerInternalNote> notes = customerInternalNoteRepository
                .findByCustomerUserIdOrderByCreatedAtDesc(userId);
        return mapNotes(notes);
    }

    @Transactional
    public CustomerInternalNoteResponse addInternalNote(Long userId, CustomerInternalNoteRequest request) {
        User customerUser = getAccessibleCustomerByUserId(userId).getUser();

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
            throw new NotFoundException("Không tìm thấy ghi chú nội bộ");
        }
        customerInternalNoteRepository.deleteById(noteId);
    }

    public List<CustomerStatusLogResponse> getStatusLogs(Long userId) {
        getAccessibleCustomerByUserId(userId);
        List<CustomerStatusLog> logs = customerStatusLogRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId);
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> userNameById = userRepository.findAllById(
                logs.stream().map(CustomerStatusLog::getCreatedByUserId).filter(id -> id != null && id > 0).distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return logs.stream()
                .map(statusLog -> CustomerStatusLogResponse.builder()
                        .id(statusLog.getId())
                        .fromStatus(statusLog.getFromStatus())
                        .toStatus(statusLog.getToStatus())
                        .reason(statusLog.getReason())
                        .changedByName(userNameById.getOrDefault(statusLog.getCreatedByUserId(), "Hệ thống"))
                        .createdAt(statusLog.getCreatedAt())
                        .build())
                .toList();
    }

    private List<CustomerInternalNoteResponse> mapNotes(List<CustomerInternalNote> notes) {
        if (notes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> authorNameById = userRepository.findAllById(
                notes.stream().map(CustomerInternalNote::getCreatedByUserId).filter(id -> id != null && id > 0)
                        .distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return notes.stream()
                .map(note -> CustomerInternalNoteResponse.builder()
                        .id(note.getId())
                        .content(note.getContent())
                        .authorName(authorNameById.getOrDefault(note.getCreatedByUserId(), "Hệ thống"))
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

    // Hàm này để gọi sau khi cập nhật trạng thái đơn hàng (Hủy, Hoàn trả, Thành
    // công)
    @Transactional
    public void evaluateAndHandleCustomerReputation(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole().getSlug().equals("ADMIN"))
            return;

        Long completedOrders = orderRepository.countCompletedOrdersByUserId(userId);
        Long settledOrders = orderRepository.countSettledOrdersByUserId(userId);

        if (settledOrders == null || settledOrders < 3) {
            // Chưa đủ dữ liệu (ít hơn 3 đơn) thì khoan hãy phạt
            return;
        }

        double reputationScore = (double) (completedOrders != null ? completedOrders : 0) / settledOrders * 100;

        // Xử lý theo Rule mới: không khóa, chỉ cảnh báo và đánh dấu rủi ro
        if (reputationScore < 50.0) {
            try {
                emailService.sendWarningEmail(user.getEmail(), user.getFullName(), reputationScore);
                log.info("Đã gửi cảnh báo tài khoản user_id {} do uy tín thấp ({}%)", userId, reputationScore);
            } catch (Exception e) {
                log.error("Lỗi gửi mail cảnh báo: {}", e.getMessage());
            }
        }
    }

    // Hàm map dữ liệu
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

        // 🟢 Assign branch & staff & internal notes
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

    // 🟢 Get all staff by branch (for FE dropdown)
    public List<Map<String, Object>> getStaffByBranch(Long branchId) {
        return userRepository.findByBranchIdAndRole(branchId, "STAFF");
    }

    // 🟢 Get all branches (for FE dropdown)
    public List<?> getAllBranches() {
        return branchRepository.findAll();
    }
}
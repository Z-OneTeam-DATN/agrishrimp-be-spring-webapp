package com.zone.agri.service;

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
import com.zone.agri.repository.*;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
        Page<User> users = userRepository.findAllCustomers(keyword, normalizedPhoneKeyword, finalStatus, pageable);
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
        Long completedOrders = Optional.ofNullable(orderRepository.countCompletedOrdersByUserId(userId))
                .orElse(0L);
        BigDecimal totalSpent = Optional.ofNullable(orderRepository.sumTotalSpentByUserId(userId))
                .orElse(BigDecimal.ZERO);

        dto.setTotalOrders(totalOrders);
        dto.setTotalSpent(totalSpent);

        double reputationScore = 0.0;
        if (totalOrders > 0) {
            double score = (double) completedOrders / totalOrders * 100;
            reputationScore = Math.round(score * 100.0) / 100.0;
        }

        dto.setReputationScore(reputationScore);
        dto.setRiskLevel(determineRiskLevel(reputationScore));
        dto.setOnlinePaymentOnly(requiresOnlinePayment(reputationScore));

        return dto;
    }

    public boolean requiresOnlinePayment(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() == null || user.getRole().getSlug().equals("ADMIN")) {
            return false;
        }

        Long totalOrders = orderRepository.countTotalOrdersByUserId(userId);
        Long completedOrders = orderRepository.countCompletedOrdersByUserId(userId);
        if (totalOrders == null || totalOrders < 3) {
            return false;
        }

        double reputationScore = (double) (completedOrders != null ? completedOrders : 0) / totalOrders * 100;
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản người dùng"));
        return convertToResponse(user);
    }

    public CustomerDetailResponse getCustomerDetailById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản người dùng"));
        CustomerResponse base = convertToResponse(user);

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
        List<CustomerInternalNote> notes = customerInternalNoteRepository
                .findByCustomerUserIdOrderByCreatedAtDesc(userId);
        return mapNotes(notes);
    }

    @Transactional
    public CustomerInternalNoteResponse addInternalNote(Long userId, CustomerInternalNoteRequest request) {
        User customerUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản người dùng"));

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

        Long totalOrders = orderRepository.countTotalOrdersByUserId(userId);
        Long completedOrders = orderRepository.countCompletedOrdersByUserId(userId);

        if (totalOrders == null || totalOrders < 3) {
            // Chưa đủ dữ liệu (ít hơn 3 đơn) thì khoan hãy phạt
            return;
        }

        double reputationScore = (double) (completedOrders != null ? completedOrders : 0) / totalOrders * 100;

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
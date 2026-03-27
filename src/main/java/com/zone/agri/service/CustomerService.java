package com.zone.agri.service;

import com.zone.agri.dto.request.customer.CustomerRequest;
import com.zone.agri.dto.response.customer.CustomerResponse;
import com.zone.agri.entity.Customer;
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
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    // 1. Tạo mới khách hàng
    @Transactional
    public Customer createCustomer(CustomerRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ConflictException("Email " + req.getEmail() + " đã có tài khoản trong hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(req.getPhone())) {
            throw new ConflictException("SĐT " + req.getPhone() + " đã được sử dụng!");
        }

        String randomPassword = RandomStringUtils.randomAlphanumeric(8);

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

        if (customer.getStatus() == null) customer.setStatus(CustomerStatus.ACTIVE);

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
        Page<User> users = userRepository.findAllCustomers(keyword, finalStatus, pageable);
        return users.map(this::convertToResponse);
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
            dto.setPhone(defaultAddr.getReceiverPhone() != null ? defaultAddr.getReceiverPhone() : user.getPhoneNumber());
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
        Long totalOrders = java.util.Optional.ofNullable(orderRepository.countTotalOrdersByUserId(userId)).orElse(0L);
        Long completedOrders = java.util.Optional.ofNullable(orderRepository.countCompletedOrdersByUserId(userId)).orElse(0L);
        BigDecimal totalSpent = java.util.Optional.ofNullable(orderRepository.sumTotalSpentByUserId(userId)).orElse(BigDecimal.ZERO);

        dto.setTotalOrders(totalOrders);
        dto.setTotalSpent(totalSpent);

        if (totalOrders > 0) {
            double score = (double) completedOrders / totalOrders * 100;
            dto.setReputationScore(Math.round(score * 100.0) / 100.0);
        } else {
            dto.setReputationScore(0.0);
        }

        return dto;
    }

    // 5. Khóa / Mở khóa tài khoản
    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản người dùng"));

        user.setStatus(user.getStatus() == UserStatus.ACTIVE ? UserStatus.INACTIVE : UserStatus.ACTIVE);
        userRepository.save(user);
    }

    public CustomerResponse getCustomerById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản người dùng"));
        return convertToResponse(user);
    }

    // Hàm này để gọi sau khi cập nhật trạng thái đơn hàng (Hủy, Hoàn trả, Thành công)
    @Transactional
    public void evaluateAndHandleCustomerReputation(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole().getSlug().equals("ADMIN")) return;

        Long totalOrders = orderRepository.countTotalOrdersByUserId(userId);
        Long completedOrders = orderRepository.countCompletedOrdersByUserId(userId);

        if (totalOrders == null || totalOrders < 3) {
            // Chưa đủ dữ liệu (ít hơn 3 đơn) thì khoan hãy phạt
            return;
        }

        double reputationScore = (double) (completedOrders != null ? completedOrders : 0) / totalOrders * 100;

        // Xử lý theo Rule
        if (reputationScore < 30.0 && user.getStatus() == UserStatus.ACTIVE) {
            // 1. Tự động Khóa
            user.setStatus(UserStatus.INACTIVE);
            userRepository.save(user);

            // Gửi email thông báo khóa
            try {
                emailService.sendAccountLockedEmail(user.getEmail(), user.getFullName(), reputationScore);
                log.info("Đã tự động KHÓA tài khoản user_id {} do uy tín quá thấp ({}%)", userId, reputationScore);
            } catch (Exception e) {
                log.error("Lỗi gửi mail khóa TK: {}", e.getMessage());
            }

        } else if (reputationScore < 50.0 && reputationScore >= 30.0) {
            // 2. Cảnh cáo (Chỉ gửi mail, không khóa)
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
    }
}
package com.zone.agri.service;

import com.zone.agri.dto.customer.CustomerRequest;
import com.zone.agri.dto.customer.CustomerResponse;
import com.zone.agri.entity.Customer;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.UserAddress;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.CustomerStatus;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.repository.*;
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
            throw new RuntimeException("Email " + req.getEmail() + " đã có tài khoản trong hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(req.getPhone())) {
            throw new RuntimeException("SĐT " + req.getPhone() + " đã được sử dụng!");
        }

        String randomPassword = RandomStringUtils.randomAlphanumeric(8);

        Role customerRole = roleRepository.findBySlug("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Role CUSTOMER chưa được cấu hình"));

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
            System.err.println("Không gửi được email: " + e.getMessage());
        }

        return savedCustomer;
    }

    // 2. Cập nhật khách hàng
    @Transactional
    public Customer updateCustomer(Long id, CustomerRequest req) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));

        if (!customer.getPhone().equals(req.getPhone()) && customerRepository.existsByPhone(req.getPhone())) {
            throw new RuntimeException("Số điện thoại mới đã được sử dụng bởi người khác!");
        }

        mapRequestToEntity(req, customer);
        return customerRepository.save(customer);
    }

    public Page<CustomerResponse> getCustomers(String keyword, String statusStr, Pageable pageable) {

        if (statusStr == null || statusStr.trim().isEmpty()) {
            statusStr = "all";
        }

        Page<User> users = userRepository.findAllCustomers(keyword, statusStr, pageable);

        return users.map(user -> {
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

            List<UserAddress> defaultAddresses = userAddressRepository.findByUserIdAndIsDefaultTrue(userId);
            if (!defaultAddresses.isEmpty()) {
                UserAddress defaultAddress = defaultAddresses.get(0);
                dto.setAddressDetail(defaultAddress.getAddressDetail());
                dto.setPhone(defaultAddress.getReceiverPhone() != null ? defaultAddress.getReceiverPhone() : user.getPhoneNumber());
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

            Long totalOrders = orderRepository.countTotalOrdersByUserId(userId);
            Long completedOrders = orderRepository.countCompletedOrdersByUserId(userId);
            java.math.BigDecimal totalSpent = orderRepository.sumTotalSpentByUserId(userId);

            dto.setTotalOrders(totalOrders != null ? totalOrders : 0L);
            dto.setTotalSpent(totalSpent != null ? totalSpent : java.math.BigDecimal.ZERO);

            if (totalOrders != null && totalOrders > 0) {
                double score = (double) (completedOrders != null ? completedOrders : 0) / totalOrders * 100;
                dto.setReputationScore(Math.round(score * 100.0) / 100.0);
            } else {
                dto.setReputationScore(0.0);
            }

            return dto;
        });
    }

    // 5. Khóa / Mở khóa tài khoản
    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản người dùng"));

        if (user.getStatus() == UserStatus.ACTIVE) {
            user.setStatus(UserStatus.INACTIVE);
        } else {
            user.setStatus(UserStatus.ACTIVE);
        }

        userRepository.save(user);
    }

    public CustomerResponse getCustomerById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản người dùng"));

        Customer customer = customerRepository.findByUserId(userId).orElse(null);

        List<UserAddress> defaultAddresses = userAddressRepository.findByUserIdAndIsDefaultTrue(userId);
        UserAddress defaultAddress = defaultAddresses.isEmpty() ? null : defaultAddresses.get(0);

        Long totalOrders = orderRepository.countTotalOrdersByUserId(userId);
        if (totalOrders == null) totalOrders = 0L;

        Long completedOrders = orderRepository.countCompletedOrdersByUserId(userId);
        if (completedOrders == null) completedOrders = 0L;

        BigDecimal totalSpent = orderRepository.sumTotalSpentByUserId(userId);
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;

        Double reputationScore = 0.0;
        if (totalOrders > 0) {
            reputationScore = (double) completedOrders / totalOrders * 100;
        }

        CustomerResponse dto = new CustomerResponse();
        dto.setUserId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setProvider(user.getProvider());
        dto.setUserStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());

        if (defaultAddress != null) {
            dto.setAddressDetail(defaultAddress.getAddressDetail());
            dto.setPhone(defaultAddress.getReceiverPhone());
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

        dto.setTotalOrders(totalOrders);
        dto.setTotalSpent(totalSpent);
        dto.setReputationScore(Math.round(reputationScore * 100.0) / 100.0);

        return dto;
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
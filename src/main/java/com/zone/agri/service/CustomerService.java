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
        // 1. Validate Email & Phone
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email " + req.getEmail() + " đã có tài khoản trong hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(req.getPhone())) {
            throw new RuntimeException("SĐT " + req.getPhone() + " đã được sử dụng!");
        }

        // 2. Sinh mật khẩu ngẫu nhiên (8 ký tự)
        String randomPassword = RandomStringUtils.randomAlphanumeric(8);

        // 3. Tạo User Entity (Tài khoản đăng nhập)
        Role customerRole = roleRepository.findBySlug("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Role CUSTOMER chưa được cấu hình"));

        User newUser = User.builder()
                .fullName(req.getName())
                .email(req.getEmail())
                .phoneNumber(req.getPhone())
                .passwordHash(passwordEncoder.encode(randomPassword)) // Mã hóa mật khẩu
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .role(customerRole)
                .build();

        // Lưu User trước để có ID (nếu cần, nhưng Cascade.ALL ở Customer sẽ lo việc này)
        // Tuy nhiên, logic tách biệt thường rõ ràng hơn
        // userRepository.save(newUser); -> Không cần nếu dùng Cascade ở Customer

        // 4. Tạo Customer Entity (Thông tin nghiệp vụ)
        Customer customer = new Customer();
        mapRequestToEntity(req, customer);

        // 👇 LIÊN KẾT USER VÀO CUSTOMER
        customer.setUser(newUser);

        if (customer.getStatus() == null) customer.setStatus(CustomerStatus.ACTIVE);

        // 5. Lưu xuống DB (Sẽ lưu cả Customer và User nhờ Cascade)
        Customer savedCustomer = customerRepository.save(customer);
        if (req.getAddressDetail() != null && !req.getAddressDetail().isEmpty()) {
            UserAddress defaultAddress = UserAddress.builder()
                    .user(newUser) // Liên kết với User vừa tạo
                    .receiverName(req.getName())
                    .receiverPhone(req.getPhone())
                    .addressDetail(req.getAddressDetail())
                    .provinceId(req.getProvinceId())
                    .districtId(req.getDistrictId())
                    .wardId(req.getWardId())
                    .isDefault(true) // Đặt làm mặc định để lúc đặt hàng nó tự hiện lên
                    .createdAt(LocalDateTime.now())
                    .build();
            userAddressRepository.save(defaultAddress);
        }
        // 6. Gửi Email thông báo (Bất đồng bộ hoặc đợi)
        // Nên bọc try-catch để lỡ lỗi mail thì vẫn tạo được khách hàng
        try {
            emailService.sendAccountInfo(req.getEmail(), req.getName(), randomPassword);
        } catch (Exception e) {
            System.err.println("Không gửi được email: " + e.getMessage());
            // Có thể lưu log để admin gửi lại sau
        }

        return savedCustomer;
    }

    // 2. Cập nhật khách hàng
    @Transactional
    public Customer updateCustomer(Long id, CustomerRequest req) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));

        // Nếu đổi số điện thoại, phải check xem số mới có trùng với ai khác không
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

        // 1. Lấy danh sách User từ Database
        Page<User> users = userRepository.findAllCustomers(keyword, statusStr,pageable);

        // 2. Chuyển đổi User thành CustomerResponse
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
            dto.setAvatarUrl(user.getAvatarUrl()); // Lấy avatar luôn

            // Lấy địa chỉ mặc định từ bảng UserAddress
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

            // Tính toán Thống kê (Chi tiêu, Đơn hàng)
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
            user.setStatus(UserStatus.INACTIVE); // Khóa tài khoản
        } else {
            user.setStatus(UserStatus.ACTIVE); // Mở khóa tài khoản
        }

        userRepository.save(user);
    }

    public CustomerResponse getCustomerById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản người dùng"));

        Customer customer = customerRepository.findByUserId(userId).orElse(null);

        // Lấy ra danh sách
        List<UserAddress> defaultAddresses = userAddressRepository.findByUserIdAndIsDefaultTrue(userId);

        // 1. Gắp địa chỉ mặc định từ Sổ địa chỉ (UserAddress)
        UserAddress defaultAddress = defaultAddresses.isEmpty() ? null : defaultAddresses.get(0);

        // 2. Tính toán thống kê từ OrderRepository
        Long totalOrders = orderRepository.countTotalOrdersByUserId(userId);
        if (totalOrders == null) totalOrders = 0L;

        Long completedOrders = orderRepository.countCompletedOrdersByUserId(userId);
        if (completedOrders == null) completedOrders = 0L;

        BigDecimal totalSpent = orderRepository.sumTotalSpentByUserId(userId);
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;

        // Tính điểm uy tín: (Đơn thành công / Tổng đơn) * 100
        Double reputationScore = 0.0;
        if (totalOrders > 0) {
            reputationScore = (double) completedOrders / totalOrders * 100;
        }

        // 3. Đổ dữ liệu ra DTO
        CustomerResponse dto = new CustomerResponse();
        dto.setUserId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setProvider(user.getProvider());
        dto.setUserStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());

        // Lấy SĐT và Địa chỉ: Ưu tiên Sổ địa chỉ mặc định > Profile Customer > User gốc
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

        // Set thông số thống kê
        dto.setTotalOrders(totalOrders);
        dto.setTotalSpent(totalSpent);
        dto.setReputationScore(Math.round(reputationScore * 100.0) / 100.0); // Làm tròn 2 chữ số thập phân

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
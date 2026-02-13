package com.zone.agri.service;

import com.zone.agri.dto.customer.CustomerRequest;
import com.zone.agri.entity.Customer;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.CustomerStatus;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.repository.CustomerRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

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

    // 3. Lấy danh sách (Search & Filter)
    public Page<Customer> getCustomers(String keyword, String statusStr, Pageable pageable) {
        CustomerStatus status = null;
        if (statusStr != null && !statusStr.isEmpty() && !"all".equalsIgnoreCase(statusStr)) {
            try {
                status = CustomerStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore invalid status
            }
        }
        return customerRepository.searchCustomers(keyword, status, pageable);
    }

    // 4. Lấy chi tiết
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));
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
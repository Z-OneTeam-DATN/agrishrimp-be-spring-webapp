package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.zone.agri.dto.response.customer.CustomerDetailResponse;
import com.zone.agri.dto.response.customer.CustomerResponse;
import com.zone.agri.entity.Customer;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.CustomerStatus;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.CustomerInternalNoteRepository;
import com.zone.agri.repository.CustomerRepository;
import com.zone.agri.repository.CustomerStatusLogRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserAddressRepository;
import com.zone.agri.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerInternalNoteRepository customerInternalNoteRepository;

    @Mock
    private CustomerStatusLogRepository customerStatusLogRepository;

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private CustomerService customerService;

    private Role customerRole;
    private User customerUser;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerRole = Role.builder()
                .slug("USER")
                .displayName("Khach hang")
                .isActive(true)
                .isSystem(true)
                .build();

        customer = Customer.builder()
                .name("Nguyen Van A")
                .phone("0909000000")
                .email("a@example.com")
                .status(CustomerStatus.ACTIVE)
                .build();
        setId(customer, 11L, "id");

        customerUser = User.builder()
                .fullName("Nguyen Van A")
                .email("a@example.com")
                .phoneNumber("0909000000")
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .role(customerRole)
                .customer(customer)
                .build();
        setId(customerUser, 1L, "id");

        customer.setUser(customerUser);

        lenient().when(userAddressRepository.findByUserIdAndIsDefaultTrue(anyLong()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void getCustomerById_shouldIgnoreOngoingOrdersWhenCalculatingRisk() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customerUser));
        when(orderRepository.countTotalOrdersByUserId(1L)).thenReturn(6L);
        when(orderRepository.countSettledOrdersByUserId(1L)).thenReturn(2L);
        when(orderRepository.countCompletedOrdersByUserId(1L)).thenReturn(1L);
        when(orderRepository.sumTotalSpentByUserId(1L)).thenReturn(new BigDecimal("1250000"));

        CustomerResponse response = customerService.getCustomerById(1L);

        assertThat(response.getTotalOrders()).isEqualTo(6L);
        assertThat(response.getReputationScore()).isEqualTo(50.0);
        assertThat(response.getRiskLevel()).isEqualTo("UNKNOWN");
        assertThat(response.getOnlinePaymentOnly()).isFalse();
    }

    @Test
    void getCustomerById_shouldUseSettledOrdersOnlyForReputationScore() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customerUser));
        when(orderRepository.countTotalOrdersByUserId(1L)).thenReturn(7L);
        when(orderRepository.countSettledOrdersByUserId(1L)).thenReturn(4L);
        when(orderRepository.countCompletedOrdersByUserId(1L)).thenReturn(2L);
        when(orderRepository.sumTotalSpentByUserId(1L)).thenReturn(new BigDecimal("2500000"));

        CustomerResponse response = customerService.getCustomerById(1L);

        assertThat(response.getReputationScore()).isEqualTo(50.0);
        assertThat(response.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(response.getOnlinePaymentOnly()).isFalse();
    }

    @Test
    void getCustomerById_shouldResolveCustomerIdentifierFromCustomerId() {
        when(userRepository.findById(11L)).thenReturn(Optional.empty());
        when(customerRepository.findById(11L)).thenReturn(Optional.of(customer));
        when(orderRepository.countTotalOrdersByUserId(1L)).thenReturn(3L);
        when(orderRepository.countSettledOrdersByUserId(1L)).thenReturn(3L);
        when(orderRepository.countCompletedOrdersByUserId(1L)).thenReturn(3L);
        when(orderRepository.sumTotalSpentByUserId(1L)).thenReturn(new BigDecimal("3200000"));

        CustomerResponse response = customerService.getCustomerById(11L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getCustomerId()).isEqualTo(11L);
        assertThat(response.getRiskLevel()).isEqualTo("LOW");
    }

    @Test
    void getCustomerDetailById_shouldSupportWebsiteUsersWithoutCustomerProfile() {
        Role websiteUserRole = Role.builder()
                .slug("USER")
                .displayName("Nguoi dung")
                .isActive(true)
                .isSystem(true)
                .build();

        User websiteUser = User.builder()
                .fullName("Tran Thi B")
                .email("b@example.com")
                .phoneNumber("0911222333")
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .role(websiteUserRole)
                .build();
        setId(websiteUser, 7L, "id");

        when(userRepository.findById(7L)).thenReturn(Optional.of(websiteUser));
        when(orderRepository.countTotalOrdersByUserId(7L)).thenReturn(2L);
        when(orderRepository.countSettledOrdersByUserId(7L)).thenReturn(2L);
        when(orderRepository.countCompletedOrdersByUserId(7L)).thenReturn(1L);
        when(orderRepository.sumTotalSpentByUserId(7L)).thenReturn(BigDecimal.ZERO);
        when(orderRepository.findLastOrderDateByUserId(7L)).thenReturn(null);
        when(orderRepository.findAverageOrderValueByUserId(7L)).thenReturn(null);
        when(userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(7L))
                .thenReturn(Collections.emptyList());
        when(customerInternalNoteRepository.findByCustomerUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(Collections.emptyList());
        when(customerStatusLogRepository.findByCustomerUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(Collections.emptyList());

        CustomerDetailResponse response = customerService.getCustomerDetailById(7L);

        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getCustomerId()).isNull();
        assertThat(response.getRiskLevel()).isEqualTo("UNKNOWN");
        assertThat(response.getInternalNotes()).isEmpty();
        assertThat(response.getStatusLogs()).isEmpty();
    }

    @Test
    void evaluateAndHandleCustomerReputation_shouldSkipWarningWhenNotEnoughSettledOrders() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customerUser));
        when(orderRepository.countSettledOrdersByUserId(1L)).thenReturn(2L);
        when(orderRepository.countCompletedOrdersByUserId(1L)).thenReturn(1L);

        customerService.evaluateAndHandleCustomerReputation(1L);

        verify(emailService, never()).sendWarningEmail(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void evaluateAndHandleCustomerReputation_shouldSendWarningForLowSettledOrderReputation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customerUser));
        when(orderRepository.countSettledOrdersByUserId(1L)).thenReturn(4L);
        when(orderRepository.countCompletedOrdersByUserId(1L)).thenReturn(1L);

        customerService.evaluateAndHandleCustomerReputation(1L);

        verify(emailService).sendWarningEmail("a@example.com", "Nguyen Van A", 25.0);
    }

    @SuppressWarnings("SameParameterValue")
    private void setId(Object obj, Long id, String fieldName) {
        try {
            java.lang.reflect.Field field = getField(obj.getClass(), fieldName);
            field.setAccessible(true);
            field.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id via reflection: " + e.getMessage(), e);
        }
    }

    private java.lang.reflect.Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return getField(clazz.getSuperclass(), name);
            }
            throw e;
        }
    }
}

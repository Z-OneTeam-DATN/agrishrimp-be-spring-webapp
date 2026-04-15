package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
                .slug("CUSTOMER")
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

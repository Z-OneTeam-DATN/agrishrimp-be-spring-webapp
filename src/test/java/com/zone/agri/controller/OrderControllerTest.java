package com.zone.agri.controller;

import com.zone.agri.entity.User;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.OrderService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    private OrderService orderService;
    private UserRepository userRepository;
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        userRepository = mock(UserRepository.class);
        orderController = new OrderController(orderService, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void placeOrder_shouldReturnGoneAndNotInvokeLegacyCheckoutFlow() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(77L);
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@example.com", "secret", List.of()));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/checkout");
        request.addHeader("User-Agent", "JUnit");

        ResponseEntity<Map<String, String>> response = orderController.placeOrder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody())
                .containsEntry("code", OrderController.LEGACY_CHECKOUT_DISABLED_CODE)
                .containsEntry("message", OrderController.LEGACY_CHECKOUT_DISABLED_MESSAGE);
        verify(userRepository).findByEmail("customer@example.com");
        verifyNoInteractions(orderService);
    }
}

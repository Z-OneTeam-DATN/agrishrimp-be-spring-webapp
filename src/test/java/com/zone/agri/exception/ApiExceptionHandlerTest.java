package com.zone.agri.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleBadRequestException_includesRegionalDebugPayload() {
        Map<String, Object> debugPayload = new LinkedHashMap<>();
        debugPayload.put("customerRegion", "SOUTH");
        debugPayload.put("deliveryProvinceId", 77);
        debugPayload.put("deliveryProvinceText", "Thi tran Dat Do, Huyen Long Dat, Ba Ria - Vung Tau");

        BadRequestException exception = new BadRequestException(
                "ORDER_PREPARE_NO_BRANCH_IN_REGION",
                "Kh\u00f4ng c\u00f3 chi nh\u00e1nh \u0111ang ho\u1ea1t \u0111\u1ed9ng trong v\u00f9ng giao h\u00e0ng c\u1ee7a b\u1ea1n.",
                debugPayload);

        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/orders/prepare"));

        ResponseEntity<Map<String, Object>> response = handler.handleBadRequestException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("code", "ORDER_PREPARE_NO_BRANCH_IN_REGION")
                .containsEntry("customerRegion", "SOUTH")
                .containsEntry("deliveryProvinceId", 77)
                .containsEntry("deliveryProvinceText", "Thi tran Dat Do, Huyen Long Dat, Ba Ria - Vung Tau");
    }
}

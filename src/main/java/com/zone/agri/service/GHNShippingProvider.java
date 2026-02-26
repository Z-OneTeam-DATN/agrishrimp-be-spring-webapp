package com.zone.agri.service;

import com.zone.agri.dto.geo.ShippingFeeParams;
import com.zone.agri.dto.geo.ShippingFeeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * GHN (Giao Hàng Nhanh) Shipping Fee Provider.
 * <p>
 * POST {url}/shipping-order/fee
 * Header: Token: {token}, ShopId: {shopId}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GHNShippingProvider implements ShippingProvider {

    private final RestTemplate restTemplate;

    @Value("${shipping.ghn.token}")
    private String token;

    @Value("${shipping.ghn.shop-id}")
    private String shopId;

    @Value("${shipping.ghn.url}")
    private String baseUrl;

    private static final BigDecimal FALLBACK_FEE = new BigDecimal("30000");
    private static final int MAX_RETRY = 2;

    @Override
    public ShippingFeeResult calculateFee(ShippingFeeParams params) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                return doCalculate(params);
            } catch (Exception e) {
                log.warn("GHN fee attempt {}/{} failed: {}", attempt + 1, MAX_RETRY, e.getMessage());
            }
        }
        // Fallback sau MAX_RETRY lần thất bại
        return ShippingFeeResult.builder()
                .totalFee(FALLBACK_FEE)
                .estimatedDays("2-3 ngày (ước tính)")
                .carrier("GHN")
                .isEstimate(true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private ShippingFeeResult doCalculate(ShippingFeeParams params) {
        String url = baseUrl + "/shipping-order/fee";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Token", token);
        headers.set("ShopId", shopId);

        Map<String, Object> body = new HashMap<>();
        body.put("from_district_id", params.getFromDistrictId());
        body.put("to_district_id", params.getToDistrictId());
        body.put("to_ward_code", params.getToWardCode());
        body.put("weight", params.getWeightGram());
        body.put("cod_value", params.getCodAmount());
        body.put("service_type_id", 2); // Giao hàng tiêu chuẩn

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

        if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
            throw new RuntimeException("GHN API error: " + (response != null ? response.get("message") : "null response"));
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        long totalFee = ((Number) data.get("total")).longValue();

        String estimatedDelivery = "2-3 ngày";
        if (data.get("expected_delivery_time") != null) {
            try {
                // Parse ISO datetime to readable string
                ZonedDateTime dt = ZonedDateTime.parse(data.get("expected_delivery_time").toString());
                estimatedDelivery = dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (Exception ignored) {
                estimatedDelivery = data.get("expected_delivery_time").toString();
            }
        }

        return ShippingFeeResult.builder()
                .totalFee(BigDecimal.valueOf(totalFee))
                .estimatedDays(estimatedDelivery)
                .carrier("GHN")
                .isEstimate(false)
                .build();
    }
}

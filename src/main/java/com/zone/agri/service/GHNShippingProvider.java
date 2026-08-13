package com.zone.agri.service;

import com.zone.agri.dto.request.geo.ShippingFeeParams;
import com.zone.agri.dto.response.geo.ShippingFeeResult;
import com.zone.agri.repository.ShippingProvider;
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

    private static final int MAX_RETRY = 2;

    @Override
    public ShippingFeeResult calculateFee(ShippingFeeParams params) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                return doCalculate(params);
            } catch (Exception e) {
                lastError = e instanceof RuntimeException
                        ? (RuntimeException) e
                        : new RuntimeException(e);
                log.warn("GHN fee attempt {}/{} failed: {}", attempt + 1, MAX_RETRY, e.getMessage());
            }
        }
        throw lastError != null ? lastError : new RuntimeException("GHN fee failed");
    }

    @SuppressWarnings("unchecked")
    private ShippingFeeResult doCalculate(ShippingFeeParams params) {
        if (params.getFromDistrictId() == null) {
            throw new RuntimeException("fromDistrictId null - branch is missing GHN district id");
        }
        if (params.getToDistrictId() == null) {
            throw new RuntimeException("toDistrictId null - delivery address is missing district id");
        }
        if (params.getToWardCode() == null || params.getToWardCode().isBlank()) {
            throw new RuntimeException("toWardCode null - delivery address is missing GHN ward code");
        }

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
        body.put("service_type_id", 2);

        log.debug("GHN fee request: from_district={}, to_district={}, to_ward_code={}, weight={}g",
                params.getFromDistrictId(), params.getToDistrictId(), params.getToWardCode(), params.getWeightGram());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

        if (response == null || !"200".equals(String.valueOf(response.get("code")))) {
            String ghnMsg = response != null ? String.valueOf(response.get("message")) : "null response";
            String ghnCode = response != null ? String.valueOf(response.get("code")) : "N/A";
            throw new RuntimeException("GHN API error code=" + ghnCode + " message=" + ghnMsg);
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        long totalFee = ((Number) data.get("total")).longValue();

        String estimatedDelivery = "2-3 ngay";
        if (data.get("expected_delivery_time") != null) {
            try {
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

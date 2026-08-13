package com.zone.agri.service;

import com.zone.agri.dto.request.geo.ShippingFeeParams;
import com.zone.agri.dto.response.geo.ShippingFeeResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GHNShippingProviderSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_GHN_SMOKE_TESTS", matches = "true")
    void calculateFee_withRealGhnCredentialsReturnsPositiveTotal() {
        String token = System.getenv("GHN_API_KEY");
        String shopId = System.getenv("GHN_SHOP_ID");
        assumeTrue(token != null && !token.isBlank(), "GHN_API_KEY is required for smoke test");
        assumeTrue(shopId != null && !shopId.isBlank(), "GHN_SHOP_ID is required for smoke test");

        GHNShippingProvider provider = new GHNShippingProvider(new RestTemplate());
        ReflectionTestUtils.setField(provider, "token", token);
        ReflectionTestUtils.setField(provider, "shopId", shopId);
        ReflectionTestUtils.setField(provider, "baseUrl", "https://online-gateway.ghn.vn/shiip/public-api/v2");

        ShippingFeeResult result = provider.calculateFee(ShippingFeeParams.builder()
                .fromDistrictId(3695)
                .toDistrictId(2090)
                .toWardCode("22407")
                .weightGram(500)
                .codAmount(30000)
                .build());

        assertThat(result.isEstimate()).isFalse();
        assertThat(result.getTotalFee()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getCarrier()).isEqualTo("GHN");
    }
}

package com.zone.agri;

import com.zone.agri.service.FinancialService;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.dto.response.user.RoleDto;
import com.zone.agri.security.CustomUserDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@TestPropertySource(properties = {
        "security.jwt.secret-key=test-secret-key-for-jwt-util-in-test",
        "security.jwt.issuer=test-issuer",
        "security.jwt.expiry-time-in-seconds=86400",
        "security.jwt.refreshable-duration=86400",
        "mnl.tmp-dir=mnt/",
        "spring.datasource.url=jdbc:h2:mem:cashbook-test;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.startup.schema-patches.enabled=false",
        "app.startup.seed-data.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "ai.base-url=http://localhost:8000"
})
class CashbookIntegrationTest {

    @Autowired
    private FinancialService financialService;

    @Test
    void testCashbookReport() {
        UserDetail userDetail = UserDetail.builder()
                .id(1L)
                .branchId(5L)
                .role(RoleDto.builder().slug("ADMIN").build())
                .build();

        CustomUserDetail principal = new CustomUserDetail(
                "tester",
                "password",
                true,
                true,
                userDetail,
                List.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        try {
            financialService.getCashbookReport(LocalDate.now().minusDays(30), LocalDate.now(), null);
            System.out.println("CASHBOOK_TEST_SUCCESS");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

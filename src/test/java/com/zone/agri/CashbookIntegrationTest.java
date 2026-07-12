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
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@ActiveProfiles("dev")
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

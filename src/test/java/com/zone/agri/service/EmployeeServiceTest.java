package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zone.agri.dto.request.employee.EmployeeCreateRequest;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Permission;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.enums.Gender;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.PermissionRepository;
import com.zone.agri.repository.RoleRepository;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vai tro chi dung workspace (ky su nong nghiep / tu van khach hang) la vai tro trung lap toan he
 * thong, khong bat buoc chon chi nhanh — moi vai tro con lai van bat buoc nhu truoc gio. Chay that
 * voi DB H2 trong bo nho (khong can Docker), chi mock EmailService (gui mail that khi tao nhan vien).
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "security.jwt.secret-key=test-secret-key-for-jwt-util-in-test",
    "security.jwt.issuer=test-issuer",
    "security.jwt.expiry-time-in-seconds=86400",
    "security.jwt.refreshable-duration=86400",
    "mnl.tmp-dir=mnt/",
    "spring.datasource.url=jdbc:h2:mem:agri-employee-test;MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.startup.schema-patches.enabled=false",
    "app.startup.seed-data.enabled=false",
    "spring.data.redis.repositories.enabled=false"
})
@Transactional
class EmployeeServiceTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private BranchRepository branchRepository;

    @MockitoBean
    private EmailService emailService;

    private Role seedRole(String slug, String... permissionCodes) {
        Set<Permission> permissions = java.util.Arrays.stream(permissionCodes)
                .map(code -> permissionRepository.save(Permission.builder()
                        .name(code)
                        .code(code)
                        .build()))
                .collect(java.util.stream.Collectors.toSet());
        return roleRepository.save(Role.builder()
                .slug(slug)
                .displayName(slug)
                .isActive(true)
                .isSystem(false)
                .permissions(permissions)
                .build());
    }

    private Branch seedBranch() {
        return branchRepository.save(Branch.builder().name("Chi nhanh test").build());
    }

    private EmployeeCreateRequest buildRequest(Long roleId, Long branchId) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        String phoneSuffix = String.format("%09d", seed % 1_000_000_000L);
        String citizenId = String.format("%012d", seed % 1_000_000_000_000L);
        return EmployeeCreateRequest.builder()
                .fullName("Nhan vien " + unique)
                .email(unique + "@agrishrimp.vn")
                .phoneNumber("0" + phoneSuffix)
                .citizenId(citizenId)
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .gender(Gender.MALE)
                .addressDetail("Dia chi test")
                .startDate(LocalDate.now())
                .status("ACTIVE")
                .roleId(roleId)
                .branchId(branchId)
                .build();
    }

    @Test
    void create_withoutBranchId_forRegularRole_throwsBadRequest() {
        Role role = seedRole("REGULAR_" + UUID.randomUUID());

        assertThatThrownBy(() -> employeeService.createEmployee(buildRequest(role.getId(), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_withoutBranchId_forAgronomistWorkspaceRole_succeeds() {
        Role role = seedRole("AGRONOMIST_" + UUID.randomUUID(), "AGRONOMIST_WORKSPACE_USE");

        var response = employeeService.createEmployee(buildRequest(role.getId(), null));

        assertThat(response.getBranch()).isNull();
    }

    @Test
    void create_withoutBranchId_forCustomerAdvisorWorkspaceRole_succeeds() {
        Role role = seedRole("ADVISOR_" + UUID.randomUUID(), "CUSTOMER_ADVISOR_USE");

        var response = employeeService.createEmployee(buildRequest(role.getId(), null));

        assertThat(response.getBranch()).isNull();
    }

    @Test
    void create_withBranchId_forRegularRole_stillSucceeds() {
        Role role = seedRole("REGULAR2_" + UUID.randomUUID());
        Branch branch = seedBranch();

        var response = employeeService.createEmployee(buildRequest(role.getId(), branch.getId()));

        assertThat(response.getBranch()).isNotNull();
        assertThat(response.getBranch().getId()).isEqualTo(branch.getId());
    }
}

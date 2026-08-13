package com.zone.agri.service;

import com.zone.agri.dto.response.admin.BranchDTO;
import com.zone.agri.entity.Branch;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BranchServiceMappingTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private BranchService branchService;

    @Test
    void mapToEntity_prefersDistrictIdAndFallsBackToLegacyDistrictCode() {
        BranchDTO dtoWithDistrictId = new BranchDTO();
        dtoWithDistrictId.setDistrictId(3695);
        dtoWithDistrictId.setDistrictCode(9999);
        Branch branchWithDistrictId = Branch.builder().build();

        ReflectionTestUtils.invokeMethod(branchService, "mapToEntity", branchWithDistrictId, dtoWithDistrictId);

        assertThat(branchWithDistrictId.getDistrictId()).isEqualTo(3695);

        BranchDTO dtoWithLegacyDistrictCode = new BranchDTO();
        dtoWithLegacyDistrictCode.setDistrictCode(3696);
        Branch branchWithLegacyDistrictCode = Branch.builder().build();

        ReflectionTestUtils.invokeMethod(branchService, "mapToEntity", branchWithLegacyDistrictCode, dtoWithLegacyDistrictCode);

        assertThat(branchWithLegacyDistrictCode.getDistrictId()).isEqualTo(3696);
    }

    @Test
    void mapToDTO_exposesDistrictIdForCurrentAndLegacyClients() {
        Branch branch = Branch.builder()
                .districtId(3695)
                .build();

        BranchDTO dto = ReflectionTestUtils.invokeMethod(branchService, "mapToDTO", branch);

        assertThat(dto).isNotNull();
        assertThat(dto.getDistrictId()).isEqualTo(3695);
        assertThat(dto.getDistrictCode()).isEqualTo(3695);
    }
}

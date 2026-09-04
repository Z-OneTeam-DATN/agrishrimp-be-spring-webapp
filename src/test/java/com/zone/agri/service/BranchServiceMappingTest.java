package com.zone.agri.service;

import com.zone.agri.dto.response.admin.BranchDTO;
import com.zone.agri.entity.Branch;
import com.zone.agri.exception.BadRequestException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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

    @Mock
    private GhnMasterDataService ghnMasterDataService;

    @Mock
    private BranchAddressCanonicalizer branchAddressCanonicalizer;

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
                .wardId(10105)
                .wardCode("550105")
                .wardName("Phường An Khánh")
                .build();

        BranchDTO dto = ReflectionTestUtils.invokeMethod(branchService, "mapToDTO", branch);

        assertThat(dto).isNotNull();
        assertThat(dto.getDistrictId()).isEqualTo(3695);
        assertThat(dto.getDistrictCode()).isEqualTo(3695);
        assertThat(dto.getWardId()).isEqualTo(10105);
        assertThat(dto.getWardCode()).isEqualTo("550105");
        assertThat(dto.getWardName()).isEqualTo("Phường An Khánh");
    }

    @Test
    void mapToEntity_persistsSelectedGhnWard() {
        BranchDTO dto = new BranchDTO();
        dto.setWardId(10105);
        dto.setWardCode("550105");
        dto.setWardName("Phường An Khánh");
        Branch branch = Branch.builder().build();

        ReflectionTestUtils.invokeMethod(branchService, "mapToEntity", branch, dto);

        assertThat(branch.getWardId()).isEqualTo(10105);
        assertThat(branch.getWardCode()).isEqualTo("550105");
        assertThat(branch.getWardName()).isEqualTo("Phường An Khánh");
    }

    @Test
    void validateShippingAddress_acceptsCanonicalGhnAddress() {
        BranchDTO dto = new BranchDTO();
        dto.setProvinceId(92);
        dto.setDistrictId(1572);
        dto.setWardId(10105);
        dto.setWardCode("550105");
        dto.setWardName("Phuong An Khanh");
        dto.setLat(10.0297D);
        dto.setLng(105.7706D);

        when(ghnMasterDataService.isDistrictInProvince(92, 1572)).thenReturn(true);
        when(ghnMasterDataService.isWardInDistrict(1572, "550105")).thenReturn(true);

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(branchService, "validateShippingAddress", dto))
                .doesNotThrowAnyException();
    }

    @Test
    void validateShippingAddress_rejectsBranchWithoutDistrict() {
        BranchDTO dto = new BranchDTO();
        dto.setProvinceId(92);
        dto.setWardCode("550105");
        dto.setLat(10.0297D);
        dto.setLng(105.7706D);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(branchService, "validateShippingAddress", dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("quan/huyen");
    }

    @Test
    void validateShippingAddress_rejectsBranchWithoutProvinceOrWard() {
        BranchDTO dtoWithoutProvince = new BranchDTO();
        dtoWithoutProvince.setDistrictId(1572);
        dtoWithoutProvince.setWardCode("550105");
        dtoWithoutProvince.setLat(10.0297D);
        dtoWithoutProvince.setLng(105.7706D);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(branchService, "validateShippingAddress", dtoWithoutProvince))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tinh/thanh pho");

        BranchDTO dtoWithoutWard = new BranchDTO();
        dtoWithoutWard.setProvinceId(92);
        dtoWithoutWard.setDistrictId(1572);
        dtoWithoutWard.setLat(10.0297D);
        dtoWithoutWard.setLng(105.7706D);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(branchService, "validateShippingAddress", dtoWithoutWard))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("phuong/xa");
    }

    @Test
    void create_rejectsZeroCoordinatesInsteadOfPersistingOceanLocation() {
        BranchDTO dto = new BranchDTO();
        dto.setBranchCode("CN-TEST");
        dto.setName("Chi nhanh test");
        dto.setProvinceId(92);
        dto.setDistrictId(1572);
        dto.setWardCode("550105");
        dto.setLat(0D);
        dto.setLng(0D);

        assertThatThrownBy(() -> branchService.create(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Toa do chi nhanh khong hop le");
    }
}

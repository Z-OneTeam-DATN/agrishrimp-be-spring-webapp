package com.zone.agri.service;

import com.zone.agri.dto.response.geo.RoutingResult;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchSearchServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private OpenRouteServiceProvider routingProvider;

    @InjectMocks
    private BranchSearchService branchSearchService;

    @BeforeEach
    void setUp() {
        setField(branchSearchService, "orsTopN", 5);
    }

    @Test
    void findBranchesForDelivery_prefersRealDistanceOverAdministrativeMatchWhenCoordinatesExist() {
        Branch nearWarehouse = branch(
                2L,
                "Nearby Warehouse",
                "WAREHOUSE",
                99,
                999,
                "W-99",
                10.0100,
                105.0100);
        Branch farStore = branch(
                1L,
                "Same Province Store",
                "STORE",
                96,
                961,
                "W-96",
                10.2500,
                105.2500);

        when(branchRepository.findByStatus(BranchStatus.ACTIVE)).thenReturn(List.of(farStore, nearWarehouse));
        when(routingProvider.getDistanceMatrix(any(), anyList())).thenReturn(new RoutingResult(
                List.of(180.0, 1200.0),
                List.of(900.0, 12000.0)));

        List<BranchWithRealDistance> result = branchSearchService.findBranchesForDelivery(
                96,
                961,
                "W-96",
                10.0000,
                105.0000);

        assertThat(result).extracting(candidate -> candidate.branch().getId())
                .containsExactly(2L, 1L);
    }

    @Test
    void findBranchesForDelivery_usesAdministrativeFallbackForBranchesWithoutCoordinates() {
        Branch sameDistrict = branch(
                2L,
                "Same District",
                "STORE",
                96,
                961,
                null,
                null,
                null);
        Branch sameProvinceLowerId = branch(
                4L,
                "Same Province A",
                "WAREHOUSE",
                96,
                962,
                null,
                null,
                null);
        Branch sameProvinceHigherId = branch(
                5L,
                "Same Province B",
                "STORE",
                96,
                963,
                null,
                null,
                null);
        Branch otherProvince = branch(
                9L,
                "Other Province",
                "STORE",
                95,
                951,
                null,
                null,
                null);

        when(branchRepository.findByStatus(BranchStatus.ACTIVE))
                .thenReturn(List.of(otherProvince, sameProvinceHigherId, sameDistrict, sameProvinceLowerId));

        List<BranchWithRealDistance> result = branchSearchService.findBranchesForDelivery(
                96,
                961,
                "W-96",
                10.0000,
                105.0000);

        assertThat(result).extracting(candidate -> candidate.branch().getId())
                .containsExactly(2L, 4L, 5L, 9L);
    }

    private Branch branch(
            Long id,
            String name,
            String branchType,
            Integer provinceId,
            Integer districtId,
            String wardCode,
            Double lat,
            Double lng) {
        Branch branch = Branch.builder()
                .name(name)
                .branchType(branchType)
                .provinceId(provinceId)
                .districtId(districtId)
                .wardCode(wardCode)
                .lat(lat)
                .lng(lng)
                .status(BranchStatus.ACTIVE)
                .build();
        setField(branch, "id", id);
        return branch;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Unable to set field " + fieldName, ex);
        }
    }

    private java.lang.reflect.Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        try {
            return type.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ex) {
            Class<?> parent = type.getSuperclass();
            if (parent == null) {
                throw ex;
            }
            return findField(parent, fieldName);
        }
    }
}

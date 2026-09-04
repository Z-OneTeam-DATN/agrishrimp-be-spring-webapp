package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zone.agri.entity.enums.VietnamRegion;
import org.junit.jupiter.api.Test;

class VietnamRegionResolverTest {

    private final VietnamRegionResolver resolver = new VietnamRegionResolver();

    @Test
    void resolve_assignsNorthToHaNoi() {
        assertThat(resolver.resolve(1, "Ha Noi")).contains(VietnamRegion.NORTH);
    }

    @Test
    void resolve_assignsCentralToDaNangKhanhHoaAndLamDong() {
        assertThat(resolver.resolve(48, "Da Nang")).contains(VietnamRegion.CENTRAL);
        assertThat(resolver.resolve(56, "Khanh Hoa")).contains(VietnamRegion.CENTRAL);
        assertThat(resolver.resolve(68, "Lam Dong")).contains(VietnamRegion.CENTRAL);
    }

    @Test
    void resolve_assignsSouthToHoChiMinhCanThoCaMauAndBaRiaVungTau() {
        assertThat(resolver.resolve(79, "TP Ho Chi Minh")).contains(VietnamRegion.SOUTH);
        assertThat(resolver.resolve(92, "Can Tho")).contains(VietnamRegion.SOUTH);
        assertThat(resolver.resolve(89, "An Giang")).contains(VietnamRegion.SOUTH);
        assertThat(resolver.resolve(96, "Ca Mau")).contains(VietnamRegion.SOUTH);
        assertThat(resolver.resolve(77, "Ba Ria - Vung Tau")).contains(VietnamRegion.SOUTH);
    }

    @Test
    void resolve_fallsBackToProvinceNameInFullAddress() {
        assertThat(resolver.resolve((Integer) null, "123 Nguyen Van Cu, Phuong 5, TP. Ca Mau"))
                .contains(VietnamRegion.SOUTH);
    }

    @Test
    void resolveBranchRegion_fallsBackToMapDisplayName() {
        com.zone.agri.entity.Branch branch = com.zone.agri.entity.Branch.builder()
                .mapDisplayName("Kho tong Can Tho, Quan Ninh Kieu, TP Can Tho")
                .build();

        assertThat(resolver.resolveBranchRegion(branch)).contains(VietnamRegion.SOUTH);
    }
}

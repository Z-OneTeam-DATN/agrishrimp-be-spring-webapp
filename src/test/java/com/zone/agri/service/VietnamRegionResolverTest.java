package com.zone.agri.service;

import com.zone.agri.entity.enums.VietnamRegion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietnamRegionResolverTest {

    private final VietnamRegionResolver resolver = new VietnamRegionResolver();

    @Test
    void resolve_assignsNorthToHaNoi() {
        assertThat(resolver.resolve(1, "Hà Nội")).contains(VietnamRegion.NORTH);
    }

    @Test
    void resolve_assignsCentralToDaNangKhanhHoaAndLamDong() {
        assertThat(resolver.resolve(48, "Đà Nẵng")).contains(VietnamRegion.CENTRAL);
        assertThat(resolver.resolve(56, "Khánh Hòa")).contains(VietnamRegion.CENTRAL);
        assertThat(resolver.resolve(68, "Lâm Đồng")).contains(VietnamRegion.CENTRAL);
    }

    @Test
    void resolve_assignsSouthToHoChiMinhAndCaMau() {
        assertThat(resolver.resolve(79, "TP. Hồ Chí Minh")).contains(VietnamRegion.SOUTH);
        assertThat(resolver.resolve(96, "Cà Mau")).contains(VietnamRegion.SOUTH);
    }

    @Test
    void resolve_fallsBackToProvinceNameInFullAddress() {
        assertThat(resolver.resolve((Integer) null, "123 Nguyen Van Cu, Phuong 5, TP. Cà Mau"))
                .contains(VietnamRegion.SOUTH);
    }
}

package com.zone.agri.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineUtilsTest {

    @Test
    void distanceKm_samePoint_shouldBeZero() {
        double dist = HaversineUtils.distanceKm(10.0, 105.0, 10.0, 105.0);
        assertThat(dist).isCloseTo(0.0, within(0.001));
    }

    @Test
    void distanceKm_canThoToSocTrang_shouldBeApprox80km() {
        // Cần Thơ: 10.0341, 105.7904
        // Sóc Trăng: 9.6000, 105.9800
        double dist = HaversineUtils.distanceKm(10.0341, 105.7904, 9.6000, 105.9800);
        // Thực tế ~60-80km đường chim bay
        assertThat(dist).isBetween(50.0, 90.0);
    }

    @Test
    void distanceKm_hanoiToHCMC_shouldBeApprox1137km() {
        // Hà Nội: 21.0285, 105.8542
        // TP.HCM: 10.8231, 106.6297
        // Khoảng cách đường chim bay (Haversine) ≈ 1137 km, không phải đường bộ ~1700 km
        double dist = HaversineUtils.distanceKm(21.0285, 105.8542, 10.8231, 106.6297);
        assertThat(dist).isBetween(1050.0, 1250.0);
    }

    @Test
    void distanceKm_isSymmetric() {
        double d1 = HaversineUtils.distanceKm(10.0, 106.0, 11.0, 107.0);
        double d2 = HaversineUtils.distanceKm(11.0, 107.0, 10.0, 106.0);
        assertThat(d1).isCloseTo(d2, within(0.001));
    }

    @Test
    void distanceKm_poleToPole_shouldBeApproximately20015km() {
        double dist = HaversineUtils.distanceKm(90.0, 0.0, -90.0, 0.0);
        // Bán chu vi Trái Đất ≈ 20015 km
        assertThat(dist).isBetween(19900.0, 20100.0);
    }
}

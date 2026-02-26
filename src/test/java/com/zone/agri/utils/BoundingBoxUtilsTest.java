package com.zone.agri.utils;

import com.zone.agri.utils.BoundingBoxUtils.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BoundingBoxUtilsTest {

    private static final double TOLERANCE = 0.0001;

    @Test
    void calculate_15km_shouldHaveCorrectBounds() {
        // Cần Thơ: 10.0341, 105.7904
        BoundingBox box = BoundingBoxUtils.calculate(10.0341, 105.7904, 15.0);

        // latDelta = 15 / 111 ≈ 0.1351
        double expectedLatDelta = 15.0 / 111.0;
        assertThat(box.minLat()).isCloseTo(10.0341 - expectedLatDelta, within(TOLERANCE));
        assertThat(box.maxLat()).isCloseTo(10.0341 + expectedLatDelta, within(TOLERANCE));
    }

    @Test
    void calculate_minMaxOrdering_shouldBeCorrect() {
        BoundingBox box = BoundingBoxUtils.calculate(10.0, 106.0, 10.0);
        assertThat(box.minLat()).isLessThan(box.maxLat());
        assertThat(box.minLng()).isLessThan(box.maxLng());
    }

    @Test
    void calculate_radiusZero_shouldReturnTinyBox() {
        BoundingBox box = BoundingBoxUtils.calculate(10.0, 106.0, 0.0);
        assertThat(box.minLat()).isCloseTo(box.maxLat(), within(TOLERANCE));
        assertThat(box.minLng()).isCloseTo(box.maxLng(), within(TOLERANCE));
    }

    @Test
    void calculate_largeRadius_shouldContainPoints() {
        // radius 100km từ Cần Thơ
        BoundingBox box = BoundingBoxUtils.calculate(10.0341, 105.7904, 100.0);

        // Sóc Trăng (9.6, 105.98) nên nằm trong box 100km
        assertThat(9.6).isBetween(box.minLat(), box.maxLat());
        assertThat(105.98).isBetween(box.minLng(), box.maxLng());
    }

    @Test
    void calculate_lngDelta_variesWithLatitude() {
        // Tại equator (lat=0): lngDelta = radius / 111
        BoundingBox boxEquator = BoundingBoxUtils.calculate(0.0, 0.0, 10.0);
        double lngDeltaEquator = boxEquator.maxLng() - 0.0;

        // Tại lat=60 độ: cos(60) = 0.5 → lngDelta gấp đôi
        BoundingBox boxHigh = BoundingBoxUtils.calculate(60.0, 0.0, 10.0);
        double lngDeltaHigh = boxHigh.maxLng() - 0.0;

        assertThat(lngDeltaHigh).isGreaterThan(lngDeltaEquator);
    }
}

package com.zone.agri.utils;

/**
 * Tính Bounding Box (hộp giới hạn) từ một điểm tâm và bán kính.
 * Dùng để filter nhanh chi nhánh theo vị trí trước khi tính Haversine.
 */
public class BoundingBoxUtils {

    private BoundingBoxUtils() {}

    /**
     * Bounding box gồm 4 biên minLat, maxLat, minLng, maxLng.
     */
    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {}

    /**
     * Tính bounding box cho tâm (userLat, userLng) với bán kính radiusKm.
     * <p>
     * Công thức:
     * <pre>
     *   latDelta  = radiusKm / 111.0
     *   lngDelta  = radiusKm / (111.0 * cos(toRadians(userLat)))
     * </pre>
     *
     * @param userLat  vĩ độ trung tâm
     * @param userLng  kinh độ trung tâm
     * @param radiusKm bán kính (km)
     * @return BoundingBox
     */
    public static BoundingBox calculate(double userLat, double userLng, double radiusKm) {
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(userLat)));

        return new BoundingBox(
                userLat - latDelta,
                userLat + latDelta,
                userLng - lngDelta,
                userLng + lngDelta
        );
    }
}

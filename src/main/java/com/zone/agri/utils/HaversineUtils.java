package com.zone.agri.utils;

/**
 * Tính khoảng cách giữa hai tọa độ theo công thức Haversine.
 * R = 6371 km (bán kính Trái Đất)
 */
public class HaversineUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private HaversineUtils() {}

    /**
     * Tính khoảng cách (km) giữa 2 điểm địa lý.
     *
     * @param lat1 vĩ độ điểm 1 (độ thập phân)
     * @param lng1 kinh độ điểm 1 (độ thập phân)
     * @param lat2 vĩ độ điểm 2 (độ thập phân)
     * @param lng2 kinh độ điểm 2 (độ thập phân)
     * @return khoảng cách tính bằng km
     */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}

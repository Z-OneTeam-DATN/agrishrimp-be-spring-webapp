package com.zone.agri.service;

import com.zone.agri.dto.response.geo.UserLocationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Xác định vị trí người dùng từ IP (fallback khi FE không gửi tọa độ).
 * Sử dụng ip-api.com — không cần API key.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final RestTemplate restTemplate;

    @Value("${location.ip-geo-url:http://ip-api.com/json}")
    private String ipGeoUrl;

    /**
     * Lấy tọa độ từ IP address.
     *
     * @param ipAddress IP của client (e.g., "10.0.0.1")
     * @return UserLocationDto hoặc null nếu không xác định được
     */
    @SuppressWarnings("unchecked")
    public UserLocationDto resolveLocationFromIp(String ipAddress) {
        try {
            // Bỏ qua localhost/private IPs
            if (ipAddress == null || ipAddress.startsWith("127.")
                    || ipAddress.startsWith("192.168.")
                    || ipAddress.startsWith("10.")
                    || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
                return null;
            }
            String url = ipGeoUrl + "/" + ipAddress;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !"success".equals(response.get("status"))) return null;

            double lat = ((Number) response.get("lat")).doubleValue();
            double lng = ((Number) response.get("lon")).doubleValue();
            String city = (String) response.get("city");
            return new UserLocationDto(lat, lng, city);
        } catch (Exception e) {
            log.warn("IP geolocation failed for IP {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }
}

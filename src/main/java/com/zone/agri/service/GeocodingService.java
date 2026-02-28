package com.zone.agri.service;

import com.zone.agri.dto.geo.CoordinateDto;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.GeocodingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Facade chọn provider geocoding theo config geocoding.provider.
 * Chỉ gọi trong admin flow (tạo/sửa branch) — KHÔNG gọi trong search flow.
 */
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final TrackAsiaGeocodingProvider trackAsia;
    private final NominatimGeocodingProvider nominatim;

    @Value("${geocoding.provider:trackasia}")
    private String provider;

    /**
     * Geocode địa chỉ thành tọa độ (lat, lng).
     *
     * @param address địa chỉ đầy đủ (ví dụ: "99 Nguyễn Văn Cừ, Cần Thơ")
     * @return CoordinateDto
     * @throws BadRequestException nếu geocode thất bại
     */
    public CoordinateDto geocode(String address) {
        GeocodingProvider p = "nominatim".equalsIgnoreCase(provider) ? nominatim : trackAsia;
        Optional<CoordinateDto> result = p.geocode(address);
        return result.orElseThrow(() ->
                new BadRequestException("Không thể xác định tọa độ cho địa chỉ: " + address));
    }
}

package com.zone.agri.repository;

import com.zone.agri.dto.geo.CoordinateDto;

import java.util.Optional;

/**
 * Interface cho các provider Geocoding.
 * Hiện tại có 2 implementation: TrackAsia và Nominatim.
 */
public interface GeocodingProvider {
    Optional<CoordinateDto> geocode(String address);
}

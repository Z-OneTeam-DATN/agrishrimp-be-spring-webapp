package com.zone.agri.service;

import com.zone.agri.dto.geo.CoordinateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrackAsiaGeocodingProvider implements GeocodingProvider {

    private final RestTemplate restTemplate;

    @Value("${geocoding.trackasia.api-key}")
    private String apiKey;

    @Value("${geocoding.trackasia.url}")
    private String url;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<CoordinateDto> geocode(String address) {
        try {
            String uri = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("query", address)
                    .queryParam("api_key", apiKey)
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
            if (response == null) return Optional.empty();

            List<Map<String, Object>> features = (List<Map<String, Object>>) response.get("features");
            if (features == null || features.isEmpty()) return Optional.empty();

            Map<String, Object> geometry = (Map<String, Object>) features.get(0).get("geometry");
            List<Double> coordinates = (List<Double>) geometry.get("coordinates");

            // TrackAsia trả về [lng, lat]
            double lng = coordinates.get(0);
            double lat = coordinates.get(1);
            return Optional.of(new CoordinateDto(lat, lng));
        } catch (Exception e) {
            log.warn("TrackAsia geocoding failed for address '{}': {}", address, e.getMessage());
            return Optional.empty();
        }
    }
}

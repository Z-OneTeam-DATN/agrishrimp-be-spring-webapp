package com.zone.agri.service;

import com.zone.agri.dto.geo.AddressSuggestionDto;
import com.zone.agri.dto.geo.CoordinateDto;
import com.zone.agri.repository.GeocodingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrackAsiaGeocodingProvider implements GeocodingProvider {

    private final RestTemplate restTemplate;

    @Value("${geocoding.trackasia.api-key}")
    private String apiKey;

    @Value("${geocoding.trackasia.url}")
    private String url;

    @Value("${geocoding.trackasia.autocomplete-url:https://api.trackasia.vn/api/geocode/autocomplete}")
    private String autocompleteUrl;

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

    @SuppressWarnings("unchecked")
    public List<AddressSuggestionDto> autocomplete(String query) {
        try {
            String uri = UriComponentsBuilder.fromHttpUrl(autocompleteUrl)
                    .queryParam("text", query)
                    .queryParam("api_key", apiKey)
                    .queryParam("lang", "vi")
                    .queryParam("boundary.country", "VN")
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
            if (response == null) return List.of();

            List<Map<String, Object>> features = (List<Map<String, Object>>) response.get("features");
            if (features == null) return List.of();

            return features.stream()
                    .map(feature -> {
                        Map<String, Object> props = (Map<String, Object>) feature.get("properties");
                        Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
                        List<Double> coords = geometry != null ? (List<Double>) geometry.get("coordinates") : null;

                        String label = (String) props.getOrDefault("label", "");
                        String province = (String) props.getOrDefault("region", "");
                        String district = (String) props.getOrDefault("county", "");
                        String ward = (String) props.getOrDefault("locality", "");
                        double lng = coords != null && coords.size() > 0 ? coords.get(0) : 0;
                        double lat = coords != null && coords.size() > 1 ? coords.get(1) : 0;

                        return new AddressSuggestionDto(label, province, district, ward, lat, lng);
                    })
                    .limit(5)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("TrackAsia autocomplete failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }
}

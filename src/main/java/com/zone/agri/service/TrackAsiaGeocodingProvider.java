package com.zone.agri.service;

import com.zone.agri.dto.response.geo.AddressSuggestionDto;
import com.zone.agri.dto.response.geo.CoordinateDto;
import com.zone.agri.repository.GeocodingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return autocomplete(query, null, null, null);
    }

    @SuppressWarnings("unchecked")
    public List<AddressSuggestionDto> autocomplete(String query, String province, String district, String ward) {
        try {
            String contextualQuery = Stream.of(query, ward, district, province)
                    .filter(this::hasText)
                    .collect(Collectors.joining(", "));

            String uri = UriComponentsBuilder.fromHttpUrl(autocompleteUrl)
                    .queryParam("text", contextualQuery)
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
                        String suggestionProvince = (String) props.getOrDefault("region", "");
                        String suggestionDistrict = (String) props.getOrDefault("county", "");
                        String suggestionWard = (String) props.getOrDefault("locality", "");
                        double lng = coords != null && coords.size() > 0 ? coords.get(0) : 0;
                        double lat = coords != null && coords.size() > 1 ? coords.get(1) : 0;

                        return new AddressSuggestionDto(label, suggestionProvince, suggestionDistrict, suggestionWard, lat, lng);
                    })
                    .filter(item -> hasText(item.getLabel()))
                    .filter(item -> matchesAdministrativeScope(item, province, district, ward))
                    .limit(12)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("TrackAsia autocomplete failed for query '{}' with scope [{}, {}, {}]: {}",
                    query, province, district, ward, e.getMessage());
            return List.of();
        }
    }

    private boolean matchesAdministrativeScope(AddressSuggestionDto suggestion, String province, String district, String ward) {
        return (matchesText(province, suggestion.getProvince()) || labelContains(suggestion.getLabel(), province))
                && (matchesText(district, suggestion.getDistrict()) || labelContains(suggestion.getLabel(), district))
                && (matchesText(ward, suggestion.getWard()) || labelContains(suggestion.getLabel(), ward));
    }

    private boolean labelContains(String label, String expected) {
        if (!hasText(expected)) {
            return true;
        }
        if (!hasText(label)) {
            return false;
        }
        String normalizedLabel = normalizeAdministrativeName(label);
        String normalizedExpected = normalizeAdministrativeName(expected);
        return normalizedLabel.contains(normalizedExpected);
    }

    private boolean matchesText(String expected, String actual) {
        if (!hasText(expected)) {
            return true;
        }
        String left = normalizeAdministrativeName(expected);
        String right = normalizeAdministrativeName(actual);
        return !right.isEmpty() && (right.contains(left) || left.contains(right));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeAdministrativeName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("^(thanh pho|tinh|quan|huyen|thi xa|thi tran|phuong|xa)\\s+", "")
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

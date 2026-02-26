package com.zone.agri.service;

import com.zone.agri.dto.geo.CoordinateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NominatimGeocodingProvider implements GeocodingProvider {

    private final RestTemplate restTemplate;

    @Value("${geocoding.nominatim.url}")
    private String url;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<CoordinateDto> geocode(String address) {
        try {
            String uri = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("q", address)
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    .toUriString();

            // Nominatim yêu cầu User-Agent
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "AgriShrimp/1.0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(uri, HttpMethod.GET, entity, List.class);
            List<Map<String, Object>> results = response.getBody();

            if (results == null || results.isEmpty()) return Optional.empty();

            Map<String, Object> first = results.get(0);
            double lat = Double.parseDouble(first.get("lat").toString());
            double lng = Double.parseDouble(first.get("lon").toString());
            return Optional.of(new CoordinateDto(lat, lng));
        } catch (Exception e) {
            log.warn("Nominatim geocoding failed for address '{}': {}", address, e.getMessage());
            return Optional.empty();
        }
    }
}

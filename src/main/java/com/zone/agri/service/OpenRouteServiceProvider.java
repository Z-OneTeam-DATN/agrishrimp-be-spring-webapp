package com.zone.agri.service;

import com.zone.agri.dto.geo.CoordinateDto;
import com.zone.agri.dto.geo.RoutingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * OpenRouteService Distance Matrix.
 * POST /v2/matrix/driving-car
 * Header: Authorization: {api-key}
 * Body: { locations: [[lng,lat], ...], sources: [0], destinations: [1,2,...] }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouteServiceProvider implements RoutingProvider {

    private final RestTemplate restTemplate;

    @Value("${routing.ors.api-key}")
    private String apiKey;

    @Value("${routing.ors.url}")
    private String url;

    @Override
    @SuppressWarnings("unchecked")
    public RoutingResult getDistanceMatrix(CoordinateDto origin, List<CoordinateDto> destinations) {
        try {
            // Build locations array: [origin] + destinations
            List<List<Double>> locations = new ArrayList<>();
            locations.add(List.of(origin.getLng(), origin.getLat())); // index 0

            List<Integer> destinationIndices = new ArrayList<>();
            for (int i = 0; i < destinations.size(); i++) {
                CoordinateDto d = destinations.get(i);
                locations.add(List.of(d.getLng(), d.getLat()));
                destinationIndices.add(i + 1);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("locations", locations);
            body.put("sources", List.of(0));
            body.put("destinations", destinationIndices);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response == null) return fallback(destinations.size());

            // durations: [[d0, d1, ...]] — 1 source, N destinations
            List<List<Double>> durations = (List<List<Double>>) response.get("durations");
            List<List<Double>> distances = (List<List<Double>>) response.get("distances");

            List<Double> durationFlat = (durations != null && !durations.isEmpty()) ? durations.get(0) : null;
            List<Double> distanceFlat = (distances != null && !distances.isEmpty()) ? distances.get(0) : null;

            return new RoutingResult(
                    durationFlat != null ? durationFlat : buildDefaultList(destinations.size(), -1.0),
                    distanceFlat != null ? distanceFlat : buildDefaultList(destinations.size(), -1.0)
            );
        } catch (Exception e) {
            log.warn("ORS Distance Matrix failed: {}", e.getMessage());
            return fallback(destinations.size());
        }
    }

    private RoutingResult fallback(int size) {
        return new RoutingResult(
                buildDefaultList(size, -1.0),
                buildDefaultList(size, -1.0)
        );
    }

    private List<Double> buildDefaultList(int size, double defaultVal) {
        List<Double> list = new ArrayList<>();
        IntStream.range(0, size).forEach(i -> list.add(defaultVal));
        return list;
    }
}

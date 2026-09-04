package com.zone.agri.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class GhnMasterDataService {

    private static final String GHN_MASTER_DATA_BASE_URL =
            "https://online-gateway.ghn.vn/shiip/public-api/master-data";

    private final RestTemplate restTemplate;

    @Value("${shipping.ghn.token}")
    private String ghnToken;

    private volatile List<Province> provinceCache = List.of();
    private final Map<Integer, List<District>> districtCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<Ward>> wardCache = new ConcurrentHashMap<>();

    public record Province(Integer id, String name) {
    }

    public record District(Integer id, String name) {
    }

    public record Ward(Integer wardId, String code, String name) {
    }

    public List<Province> getProvinces() {
        List<Province> cached = provinceCache;
        if (!cached.isEmpty()) {
            return cached;
        }

        synchronized (this) {
            if (!provinceCache.isEmpty()) {
                return provinceCache;
            }

            List<Province> provinces = fetchGhnData(GHN_MASTER_DATA_BASE_URL + "/province").stream()
                    .map(item -> new Province(
                            toInteger(item.get("ProvinceID")),
                            String.valueOf(item.getOrDefault("ProvinceName", ""))))
                    .filter(item -> item.id() != null && hasText(item.name()))
                    .sorted(Comparator.comparing(Province::name))
                    .toList();
            provinceCache = provinces;
            return provinces;
        }
    }

    public List<District> getDistricts(Integer provinceId) {
        if (provinceId == null) {
            return List.of();
        }
        return districtCache.computeIfAbsent(provinceId, this::loadDistricts);
    }

    public List<Ward> getWards(Integer districtId) {
        if (districtId == null) {
            return List.of();
        }
        return wardCache.computeIfAbsent(districtId, this::loadWards);
    }

    public boolean isDistrictInProvince(Integer provinceId, Integer districtId) {
        if (provinceId == null || districtId == null) {
            return false;
        }
        return getDistricts(provinceId).stream()
                .anyMatch(district -> Objects.equals(district.id(), districtId));
    }

    public boolean isWardInDistrict(Integer districtId, String wardCode) {
        if (districtId == null || !hasText(wardCode)) {
            return false;
        }

        String normalizedWardCode = wardCode.trim();
        return getWards(districtId).stream()
                .anyMatch(ward -> normalizedWardCode.equalsIgnoreCase(ward.code()));
    }

    public Optional<Province> findProvince(Integer provinceId, String provinceName, String fallbackText) {
        List<Province> provinces = getProvinces();
        if (provinceId != null) {
            Optional<Province> byId = provinces.stream()
                    .filter(item -> Objects.equals(item.id(), provinceId))
                    .findFirst();
            if (byId.isPresent()) {
                return byId;
            }
        }

        return findBestTextMatch(provinces, Province::name, provinceName, fallbackText);
    }

    public Optional<District> findDistrict(Integer provinceId, Integer districtId, String districtName, String fallbackText) {
        if (provinceId == null) {
            return Optional.empty();
        }

        List<District> districts = getDistricts(provinceId);
        if (districtId != null) {
            Optional<District> byId = districts.stream()
                    .filter(item -> Objects.equals(item.id(), districtId))
                    .findFirst();
            if (byId.isPresent()) {
                return byId;
            }
        }

        return findBestTextMatch(districts, District::name, districtName, fallbackText);
    }

    public Optional<District> findDistrictByWard(Integer provinceId, Integer wardId, String wardCode, String wardName, String fallbackText) {
        if (provinceId == null) {
            return Optional.empty();
        }

        for (District district : getDistricts(provinceId)) {
            Optional<Ward> ward = findWard(district.id(), wardId, wardCode, wardName, fallbackText);
            if (ward.isPresent()) {
                return Optional.of(district);
            }
        }

        return Optional.empty();
    }

    public Optional<Ward> findWard(Integer districtId, Integer wardId, String wardCode, String wardName, String fallbackText) {
        if (districtId == null) {
            return Optional.empty();
        }

        List<Ward> wards = getWards(districtId);
        if (hasText(wardCode)) {
            Optional<Ward> byCode = wards.stream()
                    .filter(item -> wardCode.trim().equalsIgnoreCase(item.code()))
                    .findFirst();
            if (byCode.isPresent()) {
                return byCode;
            }
        }

        if (wardId != null) {
            Optional<Ward> byId = wards.stream()
                    .filter(item -> Objects.equals(item.wardId(), wardId))
                    .findFirst();
            if (byId.isPresent()) {
                return byId;
            }
        }

        return findBestTextMatch(wards, Ward::name, wardName, fallbackText);
    }

    public String normalizeAdministrativeName(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("\\b(thanh pho|tp\\.?|tinh|quan|q\\.?|huyen|h\\.?|thi xa|thi tran|phuong|xa)\\b", " ")
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<District> loadDistricts(Integer provinceId) {
        return fetchGhnData(GHN_MASTER_DATA_BASE_URL + "/district?province_id=" + provinceId).stream()
                .map(item -> new District(
                        toInteger(item.get("DistrictID")),
                        String.valueOf(item.getOrDefault("DistrictName", ""))))
                .filter(item -> item.id() != null && hasText(item.name()))
                .sorted(Comparator.comparing(District::name))
                .toList();
    }

    private List<Ward> loadWards(Integer districtId) {
        return fetchGhnData(GHN_MASTER_DATA_BASE_URL + "/ward?district_id=" + districtId).stream()
                .map(item -> new Ward(
                        toInteger(item.get("WardID")),
                        stringValue(item.get("WardCode")),
                        String.valueOf(item.getOrDefault("WardName", ""))))
                .filter(item -> hasText(item.code()) && hasText(item.name()))
                .sorted(Comparator.comparing(Ward::name))
                .toList();
    }

    private <T> Optional<T> findBestTextMatch(
            List<T> items,
            Function<T, String> nameExtractor,
            String primaryText,
            String fallbackText) {
        List<String> candidates = new ArrayList<>();
        if (hasText(primaryText)) {
            candidates.add(primaryText);
        }
        if (hasText(fallbackText)) {
            candidates.add(fallbackText);
        }

        for (String candidate : candidates) {
            String normalizedCandidate = normalizeAdministrativeName(candidate);
            if (normalizedCandidate.isEmpty()) {
                continue;
            }

            Optional<T> exact = items.stream()
                    .filter(item -> normalizedCandidate.equals(normalizeAdministrativeName(nameExtractor.apply(item))))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }

            Optional<T> contains = items.stream()
                    .filter(item -> {
                        String normalizedName = normalizeAdministrativeName(nameExtractor.apply(item));
                        return !normalizedName.isEmpty()
                                && (normalizedCandidate.contains(normalizedName)
                                || normalizedName.contains(normalizedCandidate));
                    })
                    .max(Comparator.comparingInt(item -> normalizeAdministrativeName(nameExtractor.apply(item)).length()));
            if (contains.isPresent()) {
                return contains;
            }
        }

        return Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> typedMap = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                typedMap.put(String.valueOf(key), value);
            }
        });
        return typedMap;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchGhnData(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Token", ghnToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            if (response.getBody() == null) {
                throw new IllegalStateException("Khong the tai du lieu GHN luc nay");
            }

            Object data = response.getBody().get("data");
            if (data instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> toStringKeyMap((Map<?, ?>) item))
                        .toList();
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Khong the tai du lieu GHN tu '{}': {}", url, ex.getMessage());
        }

        throw new IllegalStateException("Khong the tai du lieu GHN luc nay");
    }
}

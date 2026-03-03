package com.zone.agri.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/ghn")
@RequiredArgsConstructor
public class GhnController {

    private final RestTemplate restTemplate = new RestTemplate();

    // Thay Token thật của bạn vào đây
    private final String GHN_TOKEN = "0c84d3ad-125f-11f1-8935-9e298af4d523";
    private final String BASE_URL = "https://online-gateway.ghn.vn/shiip/public-api/master-data";

    @GetMapping("/province")
    public ResponseEntity<?> getProvinces() {
        return callGhnApi(BASE_URL + "/province");
    }

    @GetMapping("/district")
    public ResponseEntity<?> getDistricts(@RequestParam("province_id") Integer provinceId) {
        return callGhnApi(BASE_URL + "/district?province_id=" + provinceId);
    }

    @GetMapping("/ward")
    public ResponseEntity<?> getWards(@RequestParam("district_id") Integer districtId) {
        return callGhnApi(BASE_URL + "/ward?district_id=" + districtId);
    }

    @GetMapping("/address-suggestions")
    public ResponseEntity<?> getSuggestions(@RequestParam String input, @RequestParam String sessiontoken) {
        // API Google Places Autocomplete
        // Link: https://maps.googleapis.com/maps/api/place/autocomplete/json
        // Các tham số quan trọng: input, components=country:vn, language=vi, key=YOUR_GOOGLE_KEY
        String url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=" + input
                + "&components=country:vn&language=vi&key=YOUR_GOOGLE_KEY&sessiontoken=" + sessiontoken;

        return restTemplate.getForEntity(url, Object.class);
    }

    private ResponseEntity<?> callGhnApi(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Token", GHN_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Lỗi gọi API GHN: " + e.getMessage());
        }
    }
}
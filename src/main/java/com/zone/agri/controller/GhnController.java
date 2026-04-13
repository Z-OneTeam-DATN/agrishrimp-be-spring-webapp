package com.zone.agri.controller;

import com.zone.agri.dto.response.geo.AddressSuggestionDto;
import com.zone.agri.service.TrackAsiaGeocodingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/api/ghn")
@RequiredArgsConstructor
public class GhnController {

    private final RestTemplate restTemplate;
    private final TrackAsiaGeocodingProvider trackAsiaProvider;

    @Value("${shipping.ghn.token}")
    private String ghnToken;

    private static final String BASE_URL = "https://online-gateway.ghn.vn/shiip/public-api/master-data";

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

    /**
     * Gợi ý địa chỉ theo chuỗi nhập vào (dùng TrackAsia Autocomplete).
     * Có thể truyền thêm province/district/ward để khóa kết quả trong địa bàn đã chọn.
     *
     * GET /api/ghn/address-suggestions?input=99 Nguyen Van Cu&province=Can Tho&district=Ninh Kieu&ward=An Hoa
     */
    @GetMapping("/address-suggestions")
    public ResponseEntity<List<AddressSuggestionDto>> getSuggestions(
            @RequestParam String input,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String ward
    ) {
        List<AddressSuggestionDto> suggestions = trackAsiaProvider.autocomplete(input, province, district, ward);
        return ResponseEntity.ok(suggestions);
    }

    private ResponseEntity<?> callGhnApi(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Token", ghnToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Lỗi gọi API GHN: " + e.getMessage());
        }
    }
}

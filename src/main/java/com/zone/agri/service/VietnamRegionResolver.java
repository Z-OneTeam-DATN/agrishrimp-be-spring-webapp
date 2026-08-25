package com.zone.agri.service;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.UserAddress;
import com.zone.agri.entity.enums.VietnamRegion;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class VietnamRegionResolver {

    private static final Set<String> NORTH_PROVINCES = Set.of(
            "ha noi",
            "ha giang",
            "cao bang",
            "bac kan",
            "tuyen quang",
            "lao cai",
            "yen bai",
            "thai nguyen",
            "lang son",
            "quang ninh",
            "bac giang",
            "phu tho",
            "vinh phuc",
            "bac ninh",
            "hai duong",
            "hai phong",
            "hung yen",
            "thai binh",
            "ha nam",
            "nam dinh",
            "ninh binh",
            "hoa binh",
            "son la",
            "dien bien",
            "lai chau"
    );

    private static final Set<String> CENTRAL_PROVINCES = Set.of(
            "thanh hoa",
            "nghe an",
            "ha tinh",
            "quang binh",
            "quang tri",
            "thua thien hue",
            "hue",
            "da nang",
            "quang nam",
            "quang ngai",
            "binh dinh",
            "phu yen",
            "khanh hoa",
            "ninh thuan",
            "binh thuan",
            "kon tum",
            "gia lai",
            "dak lak",
            "dac lac",
            "dak nong",
            "dac nong",
            "lam dong"
    );

    private static final Set<String> SOUTH_PROVINCES = Set.of(
            "ho chi minh",
            "thanh pho ho chi minh",
            "tp ho chi minh",
            "tp hcm",
            "hcm",
            "ho chi minh city",
            "binh phuoc",
            "tay ninh",
            "binh duong",
            "dong nai",
            "ba ria vung tau",
            "ba ria - vung tau",
            "ba ria",
            "vung tau",
            "long an",
            "tien giang",
            "ben tre",
            "tra vinh",
            "vinh long",
            "dong thap",
            "an giang",
            "kien giang",
            "can tho",
            "hau giang",
            "soc trang",
            "bac lieu",
            "ca mau"
    );

    private static final Map<String, VietnamRegion> REGION_BY_PROVINCE_CODE = buildRegionByProvinceCode();
    private static final Map<String, VietnamRegion> REGION_BY_PROVINCE_NAME = buildRegionByProvinceName();

    public Optional<VietnamRegion> resolve(Integer provinceId, String provinceText) {
        if (provinceId != null) {
            VietnamRegion byId = REGION_BY_PROVINCE_CODE.get(String.valueOf(provinceId));
            if (byId != null) {
                return Optional.of(byId);
            }
        }

        String normalizedText = normalizeProvinceText(provinceText);
        if (normalizedText == null) {
            return Optional.empty();
        }

        VietnamRegion exact = REGION_BY_PROVINCE_NAME.get(normalizedText);
        if (exact != null) {
            return Optional.of(exact);
        }

        return REGION_BY_PROVINCE_NAME.entrySet().stream()
                .filter(entry -> normalizedText.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public Optional<VietnamRegion> resolve(String provinceId, String provinceText) {
        Integer parsedProvinceId = parseProvinceId(provinceId);
        return resolve(parsedProvinceId, provinceText);
    }

    public Optional<VietnamRegion> resolveBranchRegion(Branch branch) {
        if (branch == null) {
            return Optional.empty();
        }

        return resolve(
                branch.getProvinceId(),
                firstNonBlank(branch.getProvinceName(), branch.getFullAddress(), branch.getAddressDetail(), branch.getName()));
    }

    public Optional<VietnamRegion> resolveUserAddressRegion(UserAddress address) {
        if (address == null) {
            return Optional.empty();
        }

        return resolve(address.getProvinceId(), address.getAddressDetail());
    }

    public Optional<VietnamRegion> resolveDeliveryRegion(Integer provinceId, String deliveryAddress) {
        return resolve(provinceId, deliveryAddress);
    }

    public Optional<VietnamRegion> resolveAdjacentRegionForNorthOrSouth(VietnamRegion customerRegion) {
        if (customerRegion == VietnamRegion.NORTH || customerRegion == VietnamRegion.SOUTH) {
            return Optional.of(VietnamRegion.CENTRAL);
        }
        return Optional.empty();
    }

    public boolean isSameRegion(Branch branch, VietnamRegion region) {
        return resolveBranchRegion(branch)
                .map(branchRegion -> branchRegion == region)
                .orElse(false);
    }

    private Integer parseProvinceId(String provinceId) {
        if (provinceId == null || provinceId.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(provinceId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String normalizeProvinceText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", " ")
                .replaceAll("\\b(thanh pho|tp\\.?|tinh)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isBlank() ? null : normalized;
    }

    private static Map<String, VietnamRegion> buildRegionByProvinceCode() {
        Map<String, VietnamRegion> regionByCode = new LinkedHashMap<>();
        addRegionCodes(regionByCode, VietnamRegion.NORTH,
                "1", "2", "4", "6", "8", "10", "11", "12", "14", "15", "17", "19", "20", "22", "24",
                "25", "26", "27", "30", "31", "33", "34", "35", "36", "37", "201", "204", "206",
                "207", "208", "210", "211", "213");
        addRegionCodes(regionByCode, VietnamRegion.CENTRAL,
                "38", "40", "42", "44", "45", "46", "48", "49", "51", "52", "54", "56", "58", "60",
                "62", "64", "66", "67", "68", "214", "215", "216", "217", "220", "222", "223", "224");
        addRegionCodes(regionByCode, VietnamRegion.SOUTH,
                "70", "74", "75", "77", "79", "80", "82", "83", "84", "86", "87", "89", "91", "92",
                "93", "94", "95", "96", "202", "203", "205", "218", "221", "228", "229", "231",
                "232", "233", "235");
        return regionByCode;
    }

    private static Map<String, VietnamRegion> buildRegionByProvinceName() {
        Map<String, VietnamRegion> regionByName = new LinkedHashMap<>();
        registerProvinceNames(regionByName, NORTH_PROVINCES, VietnamRegion.NORTH);
        registerProvinceNames(regionByName, CENTRAL_PROVINCES, VietnamRegion.CENTRAL);
        registerProvinceNames(regionByName, SOUTH_PROVINCES, VietnamRegion.SOUTH);
        return regionByName;
    }

    private static void addRegionCodes(
            Map<String, VietnamRegion> regionByCode,
            VietnamRegion region,
            String... codes) {
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                regionByCode.putIfAbsent(code, region);
            }
        }
    }

    private static void registerProvinceNames(
            Map<String, VietnamRegion> regionByName,
            Set<String> provinceNames,
            VietnamRegion region) {
        for (String provinceName : provinceNames) {
            if (provinceName != null && !provinceName.isBlank()) {
                regionByName.putIfAbsent(provinceName, region);
            }
        }
    }
}

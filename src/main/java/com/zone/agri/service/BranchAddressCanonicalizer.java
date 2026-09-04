package com.zone.agri.service;

import com.zone.agri.dto.response.admin.BranchDTO;
import com.zone.agri.entity.Branch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BranchAddressCanonicalizer {

    private final GhnMasterDataService ghnMasterDataService;

    public void canonicalize(BranchDTO dto) {
        if (dto == null) {
            return;
        }

        String detailAddress = firstNonBlank(dto.getAddressDetail(), dto.getDetailAddress());
        if (detailAddress != null) {
            dto.setAddressDetail(detailAddress);
            dto.setDetailAddress(detailAddress);
        }

        String fallbackText = buildSearchText(
                dto.getFullAddress(),
                dto.getMapDisplayName(),
                detailAddress,
                dto.getWardName(),
                dto.getDistrictName(),
                dto.getProvinceName());

        ghnMasterDataService.findProvince(
                        firstNonNull(dto.getProvinceId(), dto.getProvinceCode()),
                        dto.getProvinceName(),
                        fallbackText)
                .ifPresent(province -> {
                    dto.setProvinceId(province.id());
                    dto.setProvinceCode(province.id());
                    dto.setProvinceName(province.name());
                });

        Integer provinceId = firstNonNull(dto.getProvinceId(), dto.getProvinceCode());
        ghnMasterDataService.findDistrict(
                        provinceId,
                        firstNonNull(dto.getDistrictId(), dto.getDistrictCode()),
                        dto.getDistrictName(),
                        fallbackText)
                .or(() -> ghnMasterDataService.findDistrictByWard(
                        provinceId,
                        dto.getWardId(),
                        dto.getWardCode(),
                        dto.getWardName(),
                        fallbackText))
                .ifPresent(district -> {
                    dto.setDistrictId(district.id());
                    dto.setDistrictCode(district.id());
                    dto.setDistrictName(district.name());
                });

        ghnMasterDataService.findWard(
                        firstNonNull(dto.getDistrictId(), dto.getDistrictCode()),
                        dto.getWardId(),
                        dto.getWardCode(),
                        dto.getWardName(),
                        fallbackText)
                .ifPresent(ward -> {
                    dto.setWardId(firstNonNull(ward.wardId(), safeParseInteger(ward.code())));
                    dto.setWardCode(ward.code());
                    dto.setWardName(ward.name());
                });

        String fullAddress = firstNonBlank(
                dto.getFullAddress(),
                dto.getMapDisplayName(),
                buildDisplayAddress(detailAddress, dto.getWardName(), dto.getDistrictName(), dto.getProvinceName()));
        if (fullAddress != null) {
            dto.setFullAddress(fullAddress);
            if (!hasText(dto.getMapDisplayName())) {
                dto.setMapDisplayName(fullAddress);
            }
        }
    }

    public boolean canonicalize(Branch branch) {
        if (branch == null) {
            return false;
        }

        BranchDTO dto = new BranchDTO();
        dto.setAddressDetail(branch.getAddressDetail());
        dto.setDetailAddress(branch.getAddressDetail());
        dto.setFullAddress(branch.getFullAddress());
        dto.setMapDisplayName(branch.getMapDisplayName());
        dto.setProvinceId(branch.getProvinceId());
        dto.setProvinceCode(branch.getProvinceId());
        dto.setProvinceName(branch.getProvinceName());
        dto.setDistrictId(branch.getDistrictId());
        dto.setDistrictCode(branch.getDistrictId());
        dto.setDistrictName(branch.getDistrictName());
        dto.setWardId(branch.getWardId());
        dto.setWardCode(branch.getWardCode());
        dto.setWardName(branch.getWardName());

        canonicalize(dto);

        boolean changed = false;
        changed |= setIfChanged(branch.getAddressDetail(), dto.getAddressDetail(), branch::setAddressDetail);
        changed |= setIfChanged(branch.getFullAddress(), dto.getFullAddress(), branch::setFullAddress);
        changed |= setIfChanged(branch.getMapDisplayName(), dto.getMapDisplayName(), branch::setMapDisplayName);
        changed |= setIfChanged(branch.getProvinceId(), dto.getProvinceId(), branch::setProvinceId);
        changed |= setIfChanged(branch.getProvinceName(), dto.getProvinceName(), branch::setProvinceName);
        changed |= setIfChanged(branch.getDistrictId(), dto.getDistrictId(), branch::setDistrictId);
        changed |= setIfChanged(branch.getDistrictName(), dto.getDistrictName(), branch::setDistrictName);
        changed |= setIfChanged(branch.getWardId(), dto.getWardId(), branch::setWardId);
        changed |= setIfChanged(branch.getWardCode(), dto.getWardCode(), branch::setWardCode);
        changed |= setIfChanged(branch.getWardName(), dto.getWardName(), branch::setWardName);
        return changed;
    }

    public String buildDisplayAddress(Branch branch) {
        if (branch == null) {
            return "";
        }

        return firstNonBlank(
                branch.getFullAddress(),
                branch.getMapDisplayName(),
                buildDisplayAddress(
                        branch.getAddressDetail(),
                        branch.getWardName(),
                        branch.getDistrictName(),
                        branch.getProvinceName()),
                "");
    }

    public boolean hasCanonicalDeliveryMetadata(Branch branch) {
        return branch != null
                && branch.getProvinceId() != null
                && branch.getDistrictId() != null
                && hasText(branch.getWardCode())
                && ghnMasterDataService.isDistrictInProvince(branch.getProvinceId(), branch.getDistrictId())
                && ghnMasterDataService.isWardInDistrict(branch.getDistrictId(), branch.getWardCode());
    }

    private <T> boolean setIfChanged(T currentValue, T nextValue, java.util.function.Consumer<T> consumer) {
        if (Objects.equals(currentValue, nextValue)) {
            return false;
        }
        consumer.accept(nextValue);
        return true;
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private String buildDisplayAddress(String detailAddress, String wardName, String districtName, String provinceName) {
        List<String> parts = new ArrayList<>();
        if (hasText(detailAddress)) {
            parts.add(detailAddress.trim());
        }
        if (hasText(wardName)) {
            parts.add(wardName.trim());
        }
        if (hasText(districtName)) {
            parts.add(districtName.trim());
        }
        if (hasText(provinceName)) {
            parts.add(provinceName.trim());
        }
        if (parts.isEmpty()) {
            return null;
        }
        parts.add("Viet Nam");
        return String.join(", ", parts);
    }

    private String buildSearchText(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (hasText(part)) {
                values.add(part.trim());
            }
        }
        return String.join(", ", values);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Integer safeParseInteger(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

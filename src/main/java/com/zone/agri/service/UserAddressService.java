package com.zone.agri.service;

import com.zone.agri.dto.request.address.UserAddressRequest;
import com.zone.agri.entity.User;
import com.zone.agri.entity.UserAddress;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.UserAddressRepository;
import com.zone.agri.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAddressService {

    private static final String GHN_MASTER_DATA_BASE_URL = "https://online-gateway.ghn.vn/shiip/public-api/master-data";

    private final UserAddressRepository addressRepo;
    private final UserRepository userRepo;
    private final RestTemplate restTemplate;

    @Value("${shipping.ghn.token}")
    private String ghnToken;

    // LẤY DANH SÁCH
    public List<UserAddress> getUserAddresses(Long userId) {
        return addressRepo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    // THÊM MỚI
    @Transactional
    public UserAddress addAddress(Long userId, UserAddressRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        sanitizeAndValidateRequest(request);

        boolean isDefault = request.getIsDefault() != null ? request.getIsDefault() : false;
        if (addressRepo.countByUserId(userId) == 0) {
            isDefault = true; // Địa chỉ đầu tiên auto là mặc định
        }

        if (isDefault) resetDefaultAddress(userId);

        UserAddress newAddress = UserAddress.builder()
                .user(user)
                .receiverName(request.getReceiverName())
                .receiverPhone(request.getReceiverPhone())
                .provinceId(request.getProvinceId() != null ? String.valueOf(request.getProvinceId()) : null)
                .districtId(request.getDistrictId() != null ? String.valueOf(request.getDistrictId()) : null)
                .wardId(request.getWardCode())   // wardId column lưu GHN WardCode (string "550113")
                .addressDetail(request.getAddressDetail())
                .lat(request.getLat())
                .lng(request.getLng())
                .isDefault(isDefault)
                .createdAt(LocalDateTime.now())
                .build();

        return addressRepo.save(newAddress);
    }

    // CẬP NHẬT
    @Transactional
    public UserAddress updateAddress(Long userId, Long addressId, UserAddressRequest request) {
        UserAddress existingAddress = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ"));
        sanitizeAndValidateRequest(request);

        boolean isDefault = request.getIsDefault() != null ? request.getIsDefault() : false;

        if (isDefault && !existingAddress.getIsDefault()) {
            resetDefaultAddress(userId);
        } else if (!isDefault && existingAddress.getIsDefault()) {
            throw new BadRequestException("Vui lòng chọn địa chỉ khác làm mặc định trước khi bỏ tick.");
        }

        existingAddress.setReceiverName(request.getReceiverName());
        existingAddress.setReceiverPhone(request.getReceiverPhone());
        existingAddress.setProvinceId(request.getProvinceId() != null ? String.valueOf(request.getProvinceId()) : null);
        existingAddress.setDistrictId(request.getDistrictId() != null ? String.valueOf(request.getDistrictId()) : null);
        existingAddress.setWardId(request.getWardCode());   // wardId column lưu GHN WardCode
        existingAddress.setAddressDetail(request.getAddressDetail());
        existingAddress.setLat(request.getLat());
        existingAddress.setLng(request.getLng());
        existingAddress.setIsDefault(isDefault);

        return addressRepo.save(existingAddress);
    }

    // XÓA
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ"));

        if (address.getIsDefault()) {
            throw new BadRequestException("Không thể xóa địa chỉ mặc định.");
        }
        addressRepo.delete(address);
    }

    // THIẾT LẬP MẶC ĐỊNH
    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        UserAddress address = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ"));

        if (!address.getIsDefault()) {
            resetDefaultAddress(userId);
            address.setIsDefault(true);
            addressRepo.save(address);
        }
    }

    private void resetDefaultAddress(Long userId) {
        List<UserAddress> oldDefaults = addressRepo.findByUserIdAndIsDefaultTrue(userId);
        for (UserAddress addr : oldDefaults) {
            addr.setIsDefault(false);
        }
        addressRepo.saveAll(oldDefaults);
    }

    private void sanitizeAndValidateRequest(UserAddressRequest request) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu địa chỉ không hợp lệ");
        }

        request.setReceiverName(requireTrimmedValue(request.getReceiverName(), "Tên người nhận không được để trống"));
        request.setReceiverPhone(requireTrimmedValue(request.getReceiverPhone(), "Số điện thoại người nhận không được để trống"));
        request.setAddressDetail(requireTrimmedValue(request.getAddressDetail(), "Địa chỉ chi tiết không được để trống"));
        request.setWardCode(requireTrimmedValue(request.getWardCode(), "Phường/Xã không được để trống"));

        if (request.getProvinceId() == null || request.getDistrictId() == null) {
            throw new BadRequestException("Tỉnh/Thành phố và Quận/Huyện không được để trống");
        }

        normalizeCoordinates(request);
        validateAdministrativeScope(request.getProvinceId(), request.getDistrictId(), request.getWardCode());
    }

    private String requireTrimmedValue(String value, String message) {
        if (value == null) {
            throw new BadRequestException(message);
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new BadRequestException(message);
        }

        return normalized;
    }

    private void normalizeCoordinates(UserAddressRequest request) {
        if ((request.getLat() == null) != (request.getLng() == null)) {
            throw new BadRequestException(
                    "T\u1ecda \u0111\u1ed9 \u0111\u1ecba ch\u1ec9 ph\u1ea3i c\u00f3 \u0111\u1ee7 c\u1eb7p lat/lng ho\u1eb7c b\u1ecf tr\u1ed1ng c\u1ea3 hai");
        }

        if (request.getLat() == null) {
            return;
        }

        Double lat = request.getLat();
        Double lng = request.getLng();
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException("T\u1ecda \u0111\u1ed9 \u0111\u1ecba ch\u1ec9 kh\u00f4ng h\u1ee3p l\u1ec7");
        }
    }

    private void validateAdministrativeScope(Long provinceId, Long districtId, String wardCode) {
        if (!isDistrictInProvince(provinceId, districtId)) {
            throw new BadRequestException("Quận/Huyện không thuộc Tỉnh/Thành đã chọn");
        }

        if (!isWardInDistrict(districtId, wardCode)) {
            throw new BadRequestException("Phường/Xã không thuộc Quận/Huyện đã chọn");
        }
    }

    private boolean isDistrictInProvince(Long provinceId, Long districtId) {
        List<Map<String, Object>> districts = fetchGhnData(
                GHN_MASTER_DATA_BASE_URL + "/district?province_id=" + provinceId);

        return districts.stream()
                .map(item -> item.get("DistrictID"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .anyMatch(value -> value.equals(String.valueOf(districtId)));
    }

    private boolean isWardInDistrict(Long districtId, String wardCode) {
        List<Map<String, Object>> wards = fetchGhnData(
                GHN_MASTER_DATA_BASE_URL + "/ward?district_id=" + districtId);

        return wards.stream()
                .map(item -> item.get("WardCode"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .anyMatch(value -> value.equalsIgnoreCase(wardCode));
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
                throw new BadRequestException("Không thể xác thực dữ liệu địa chỉ lúc này");
            }

            Object data = response.getBody().get("data");
            if (data instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> toStringKeyMap((Map<?, ?>) item))
                        .toList();
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Không thể tải dữ liệu GHN để kiểm tra địa chỉ: {}", ex.getMessage());
        }

        throw new BadRequestException("Không thể xác thực dữ liệu địa chỉ lúc này");
    }
}

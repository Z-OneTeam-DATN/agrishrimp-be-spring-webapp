package com.zone.agri.service;

import com.zone.agri.dto.request.driver.DriverRequest;
import com.zone.agri.dto.response.driver.DriverResponse;
import com.zone.agri.entity.Driver;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.DriverStatus;
import com.zone.agri.repository.DriverRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;

    @Transactional
    public DriverResponse createDriver(DriverRequest request) {
        if (request.getCode() != null && !request.getCode().isBlank()) {
            if (driverRepository.existsByCode(request.getCode().trim())) {
                throw new IllegalArgumentException("Mã tài xế " + request.getCode() + " đã tồn tại");
            }
        }
        if (driverRepository.existsByPhone(request.getPhone().trim())) {
            throw new IllegalArgumentException("Số điện thoại " + request.getPhone() + " đã tồn tại");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (driverRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
                throw new IllegalArgumentException("Email " + request.getEmail() + " đã tồn tại");
            }
        }

        Driver driver = new Driver();
        mapRequestToEntity(request, driver);
        
        if (driver.getCode() == null || driver.getCode().isBlank()) {
            driver.setCode("TX-" + System.currentTimeMillis());
        }

        Driver saved = driverRepository.save(driver);
        return buildDriverResponse(saved);
    }

    @Transactional
    public DriverResponse updateDriver(Long id, DriverRequest request) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế"));

        if (request.getCode() != null && !request.getCode().isBlank()) {
            if (driverRepository.existsByCodeAndIdNot(request.getCode().trim(), id)) {
                throw new IllegalArgumentException("Mã tài xế mới đã tồn tại");
            }
        }
        if (driverRepository.existsByPhoneAndIdNot(request.getPhone().trim(), id)) {
            throw new IllegalArgumentException("Số điện thoại mới đã tồn tại");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (driverRepository.existsByEmailAndIdNot(request.getEmail().toLowerCase().trim(), id)) {
                throw new IllegalArgumentException("Email mới đã tồn tại");
            }
        }

        mapRequestToEntity(request, driver);
        Driver updated = driverRepository.save(driver);
        return buildDriverResponse(updated);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverById(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế"));
        return buildDriverResponse(driver);
    }

    @Transactional(readOnly = true)
    public Page<DriverResponse> getAllDrivers(String keyword, String statusStr, Pageable pageable) {
        DriverStatus status = null;
        if (statusStr != null && !statusStr.isBlank() && !statusStr.equalsIgnoreCase("all")) {
            try {
                status = DriverStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception e) {
                // ignore invalid status
            }
        }
        Page<Driver> drivers = driverRepository.searchDrivers(keyword, status, pageable);
        
        // Batch resolve audit user names
        List<Long> auditUserIds = drivers.getContent().stream()
                .flatMap(d -> java.util.stream.Stream.of(d.getCreatedByUserId(), d.getUpdatedByUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> userNames = resolveUserNamesSafely(auditUserIds);

        return drivers.map(d -> {
            DriverResponse resp = DriverResponse.fromEntity(d);
            resp.setCreatedByName(getMapValueOrNull(userNames, d.getCreatedByUserId()));
            resp.setUpdatedByName(getMapValueOrNull(userNames, d.getUpdatedByUserId()));
            return resp;
        });
    }

    @Transactional
    public void deleteDriver(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài xế"));
        driverRepository.delete(driver);
    }

    private void mapRequestToEntity(DriverRequest request, Driver driver) {
        if (request.getCode() != null && !request.getCode().isBlank()) {
            driver.setCode(request.getCode().trim());
        }
        driver.setFullName(normalizeRequiredText(request.getFullName()));
        driver.setPhone(normalizeRequiredText(request.getPhone()));
        driver.setEmail(normalizeEmail(request.getEmail()));
        driver.setIdCard(normalizeRequiredText(request.getIdCard()));
        driver.setLicenseNumber(normalizeRequiredText(request.getLicenseNumber()));
        driver.setLicenseClass(normalizeRequiredText(request.getLicenseClass()));
        driver.setAvatarUrl(normalizeOptionalText(request.getAvatarUrl()));
        driver.setLicenseImageUrl(normalizeOptionalText(request.getLicenseImageUrl()));
        driver.setStatus(request.getStatus());
        driver.setVehicleNumber(normalizeOptionalText(request.getVehicleNumber()));
        driver.setVehicleType(normalizeOptionalText(request.getVehicleType()));
    }

    private DriverResponse buildDriverResponse(Driver driver) {
        DriverResponse response = DriverResponse.fromEntity(driver);
        Map<Long, String> userNames = resolveUserNamesSafely(List.of(
                driver.getCreatedByUserId(),
                driver.getUpdatedByUserId()
        ));
        response.setCreatedByName(getMapValueOrNull(userNames, driver.getCreatedByUserId()));
        response.setUpdatedByName(getMapValueOrNull(userNames, driver.getUpdatedByUserId()));
        return response;
    }

    private Map<Long, String> resolveUserNames(Collection<Long> userIds) {
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        this::resolveDisplayName,
                        (l, r) -> l,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, String> resolveUserNamesSafely(Collection<Long> userIds) {
        try {
            return resolveUserNames(userIds);
        } catch (Exception e) {
            log.warn("Unable to resolve driver audit usernames for userIds={}.", userIds, e);
            return Map.of();
        }
    }

    private String resolveDisplayName(User user) {
        if (user == null) return null;
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
        return "User #" + user.getId();
    }

    private String getMapValueOrNull(Map<Long, String> values, Long key) {
        if (key == null || values == null || values.isEmpty()) return null;
        return values.get(key);
    }

    private String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? null : normalized.toLowerCase();
    }
}

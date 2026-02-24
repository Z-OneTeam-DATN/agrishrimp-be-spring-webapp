package com.zone.agri.service;

import com.zone.agri.dto.address.UserAddressRequest;
import com.zone.agri.entity.User;
import com.zone.agri.entity.UserAddress;
import com.zone.agri.repository.UserAddressRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressRepository addressRepo;
    private final UserRepository userRepo;

    // LẤY DANH SÁCH
    public List<UserAddress> getUserAddresses(Long userId) {
        return addressRepo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    // THÊM MỚI
    @Transactional
    public UserAddress addAddress(Long userId, UserAddressRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        boolean isDefault = request.getIsDefault() != null ? request.getIsDefault() : false;
        if (addressRepo.countByUserId(userId) == 0) {
            isDefault = true; // Địa chỉ đầu tiên auto là mặc định
        }

        if (isDefault) resetDefaultAddress(userId);

        UserAddress newAddress = UserAddress.builder()
                .user(user)
                .receiverName(request.getReceiverName())
                .receiverPhone(request.getReceiverPhone())
                .provinceId(String.valueOf(request.getProvinceId())) // Lưu mã API
                .districtId(String.valueOf(request.getDistrictId())) // Lưu mã API
                .wardId(String.valueOf(request.getWardId()))         // Lưu mã API
                .addressDetail(request.getAddressDetail())           // Lưu chuỗi đầy đủ Frontend gửi lên
                .isDefault(isDefault)
                .createdAt(LocalDateTime.now())
                .build();

        return addressRepo.save(newAddress);
    }

    // CẬP NHẬT
    @Transactional
    public UserAddress updateAddress(Long userId, Long addressId, UserAddressRequest request) {
        UserAddress existingAddress = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa chỉ"));

        boolean isDefault = request.getIsDefault() != null ? request.getIsDefault() : false;

        if (isDefault && !existingAddress.getIsDefault()) {
            resetDefaultAddress(userId);
        } else if (!isDefault && existingAddress.getIsDefault()) {
            throw new RuntimeException("Vui lòng chọn địa chỉ khác làm mặc định trước khi bỏ tick.");
        }

        existingAddress.setReceiverName(request.getReceiverName());
        existingAddress.setReceiverPhone(request.getReceiverPhone());
        existingAddress.setProvinceId(String.valueOf(request.getProvinceId()));
        existingAddress.setDistrictId(String.valueOf(request.getDistrictId()));
        existingAddress.setWardId(String.valueOf(request.getWardId()));
        existingAddress.setAddressDetail(request.getAddressDetail());
        existingAddress.setIsDefault(isDefault);

        return addressRepo.save(existingAddress);
    }

    // XÓA
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa chỉ"));

        if (address.getIsDefault()) {
            throw new RuntimeException("Không thể xóa địa chỉ mặc định.");
        }
        addressRepo.delete(address);
    }

    // THIẾT LẬP MẶC ĐỊNH
    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        UserAddress address = addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa chỉ"));

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
}
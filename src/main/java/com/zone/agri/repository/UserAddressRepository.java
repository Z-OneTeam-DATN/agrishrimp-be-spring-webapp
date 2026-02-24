package com.zone.agri.repository;

import com.zone.agri.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    // Lấy danh sách địa chỉ của User, ưu tiên địa chỉ Mặc định lên đầu, sau đó sắp xếp theo ngày tạo mới nhất
    List<UserAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    // Tìm chính xác 1 địa chỉ của 1 user (để bảo mật, tránh user này sửa/xóa địa chỉ user khác)
    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    // Tìm các địa chỉ đang được set là mặc định của 1 user
    List<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);

    // Đếm xem user có bao nhiêu địa chỉ
    long countByUserId(Long userId);
}
package com.zone.agri.repository;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.enums.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    // 1. Tìm kiếm chính xác (Dùng để lấy chi tiết hoặc validate)
    Optional<Branch> findByBranchCode(String branchCode);

    // 2. Kiểm tra tồn tại (Dùng để chặn trùng lặp khi Tạo/Sửa)
    boolean existsByBranchCode(String branchCode);
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);

    // 3. Lọc danh sách (Dùng cho trang danh sách chi nhánh)
    // Lấy tất cả chi nhánh đang hoạt động (ACTIVE)
    List<Branch> findByStatus(BranchStatus status);

    // Lấy danh sách chi nhánh theo Tỉnh/Thành (Ví dụ: Lấy hết chi nhánh ở Cần Thơ)
    List<Branch> findByProvinceId(Integer provinceId);
}
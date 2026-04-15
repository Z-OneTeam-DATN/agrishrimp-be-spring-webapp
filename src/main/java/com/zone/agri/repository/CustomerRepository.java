package com.zone.agri.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zone.agri.entity.Customer;
import com.zone.agri.entity.enums.UserStatus;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

        // Kiểm tra số điện thoại đã tồn tại chưa (tránh trùng lặp)
        boolean existsByPhone(String phone);

        Optional<Customer> findByUserId(Long userId);

        // Tìm kiếm khách hàng theo tên hoặc SĐT, lọc theo trạng thái
        @Query("SELECT c FROM Customer c JOIN c.user u LEFT JOIN c.assignedBranch b WHERE " +
                        "(:keyword IS NULL OR :keyword = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR c.phone LIKE CONCAT('%', :keyword, '%') " +
                        "OR u.phoneNumber LIKE CONCAT('%', :phoneKeyword, '%')) AND " +
                        "(:status IS NULL OR u.status = :status) AND " +
                        "(:branchId IS NULL OR b.id = :branchId)")
        Page<Customer> searchCustomers(
                        @Param("keyword") String keyword,
                        @Param("phoneKeyword") String phoneKeyword,
                        @Param("branchId") Long branchId,
                        @Param("status") UserStatus status,
                        Pageable pageable);
}
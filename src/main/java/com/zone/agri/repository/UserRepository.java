package com.zone.agri.repository;

import com.zone.agri.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByCitizenId(String citizenId);

    @Query("SELECT u FROM User u WHERE " +
           "(:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.email LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.phoneNumber LIKE CONCAT('%', :keyword, '%') OR u.citizenId LIKE CONCAT('%', :keyword, '%')) AND " +
           "(:roleId IS NULL OR u.role.id = :roleId) AND " +
           "(:branchId IS NULL OR u.branch.id = :branchId) AND " +
           "(:status IS NULL OR u.status = :status)")
    Page<User> findAllWithFilter(
            @Param("keyword") String keyword,
            @Param("roleId") Long roleId,
            @Param("branchId") Long branchId,
            @Param("status") com.zone.agri.entity.enums.UserStatus status,
            Pageable pageable);

    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);
    boolean existsByCitizenIdAndIdNot(String citizenId, Long id);

    @EntityGraph(attributePaths = {"customer"})
    @Query("SELECT u FROM User u WHERE u.role.slug IN ('CUSTOMER', 'USER') " +
            "AND (:status = 'all' OR CAST(u.status AS string) = :status) " +
            "AND (:keyword IS NULL OR :keyword = '' OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR u.phoneNumber LIKE CONCAT('%', :keyword, '%')) " +
            "ORDER BY u.createdAt DESC")
    Page<User> findAllCustomers(@Param("keyword") String keyword, @Param("status") String status, Pageable pageable);
}


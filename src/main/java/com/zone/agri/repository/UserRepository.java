package com.zone.agri.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zone.agri.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

        Optional<User> findByEmail(String email);

        Optional<User> findByPhoneNumber(String phoneNumber);

        Optional<User> findByZaloId(String zaloId);

        Optional<User> findByCitizenId(String citizenId);

        boolean existsByEmail(String email);

        boolean existsByPhoneNumber(String phoneNumber);

        boolean existsByCitizenId(String citizenId);

        @Query("SELECT u FROM User u WHERE " +
                        "(:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.email LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.phoneNumber LIKE CONCAT('%', :keyword, '%') OR u.citizenId LIKE CONCAT('%', :keyword, '%')) AND "
                        +
                        "(:roleId IS NULL OR u.role.id = :roleId) AND " +
                        "(:branchId IS NULL OR u.branch.id = :branchId) AND " +
                        "(:status IS NULL OR u.status = :status)")
        Page<User> findAllWithFilter(
                        @Param("keyword") String keyword,
                        @Param("roleId") Long roleId,
                        @Param("branchId") Long branchId,
                        @Param("status") com.zone.agri.entity.enums.UserStatus status,
                        Pageable pageable);

        @Query("SELECT u FROM User u WHERE u.role.slug IN ('CUSTOMER', 'USER') " +
                        "AND (:branchId IS NULL OR u.branch.id = :branchId) " +
                        "ORDER BY u.createdAt DESC")
        List<User> findRecentCustomers(@Param("branchId") Long branchId, Pageable pageable);

        @Query("SELECT COUNT(u) FROM User u WHERE u.role.slug IN ('CUSTOMER', 'USER') " +
                        "AND (:branchId IS NULL OR u.branch.id = :branchId)")
        long countCustomers(@Param("branchId") Long branchId);

        boolean existsByEmailAndIdNot(String email, Long id);

        boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

        boolean existsByCitizenIdAndIdNot(String citizenId, Long id);

        @EntityGraph(attributePaths = { "customer" })
        @Query("SELECT u FROM User u WHERE u.role.slug IN ('CUSTOMER', 'USER') " +
                        "AND (:status = 'all' OR CAST(u.status AS string) = :status) " +
                        "AND (:keyword IS NULL OR :keyword = '' OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
                        +
                        "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR u.phoneNumber LIKE CONCAT('%', :keyword, '%') " +
                        "OR (:phoneKeyword IS NOT NULL AND :phoneKeyword <> '' AND " +
                        "CAST(FUNCTION('replace', FUNCTION('replace', FUNCTION('replace', COALESCE(u.phoneNumber, ''), ' ', ''), '.', ''), '-', '') AS STRING) LIKE CONCAT('%', :phoneKeyword, '%'))) "
                        +
                        "ORDER BY u.createdAt DESC")
        Page<User> findAllCustomers(
                        @Param("keyword") String keyword,
                        @Param("phoneKeyword") String phoneKeyword,
                        @Param("status") String status,
                        Pageable pageable);

        @EntityGraph(attributePaths = { "customer" })
        @Query("SELECT u FROM User u LEFT JOIN u.customer c LEFT JOIN c.assignedBranch b WHERE " +
                        "u.role.slug IN ('CUSTOMER', 'USER') AND " +
                        "(:status IS NULL OR u.status = :status) AND " +
                        "(:branchId IS NULL OR b.id = :branchId) AND " +
                        "(:keyword IS NULL OR :keyword = '' OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
                        +
                        "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "OR u.phoneNumber LIKE CONCAT('%', :keyword, '%') " +
                        "OR (:phoneKeyword IS NOT NULL AND :phoneKeyword <> '' AND " +
                        "CAST(FUNCTION('replace', FUNCTION('replace', FUNCTION('replace', COALESCE(u.phoneNumber, ''), ' ', ''), '.', ''), '-', '') AS STRING) LIKE CONCAT('%', :phoneKeyword, '%')))")
        Page<User> searchCustomerUsers(
                        @Param("keyword") String keyword,
                        @Param("phoneKeyword") String phoneKeyword,
                        @Param("status") com.zone.agri.entity.enums.UserStatus status,
                        @Param("branchId") Long branchId,
                        Pageable pageable);

        // 🟢 Get staff by branch
        @Query("SELECT NEW MAP(u.id AS id, u.fullName AS fullName, u.email AS email, u.phoneNumber AS phoneNumber) " +
                        "FROM User u WHERE u.branch.id = :branchId AND u.role.slug = 'STAFF' ORDER BY u.fullName")
        List<Map<String, Object>> findByBranchIdAndRole(@Param("branchId") Long branchId, @Param("slug") String slug);
}

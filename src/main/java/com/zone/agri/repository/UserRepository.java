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

        Optional<User> findFirstByRole_SlugOrderByIdAsc(String slug);

        List<User> findAllByRole_Slug(String slug);

        long countByRole_Slug(String slug);

        Optional<User> findByCitizenId(String citizenId);

        boolean existsByEmail(String email);

        boolean existsByPhoneNumber(String phoneNumber);

        boolean existsByCitizenId(String citizenId);

        @Query("SELECT DISTINCT u FROM User u LEFT JOIN u.role r LEFT JOIN r.permissions p WHERE " +
                        "(:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.email LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.phoneNumber LIKE CONCAT('%', :keyword, '%') OR u.citizenId LIKE CONCAT('%', :keyword, '%')) AND "
                        +
                        "(:roleId IS NULL OR u.role.id = :roleId) AND " +
                        "(:branchId IS NULL OR u.branch.id = :branchId) AND " +
                        "(:permissionCode IS NULL OR p.code = :permissionCode) AND " +
                        "(:status IS NULL OR u.status = :status)")
        Page<User> findAllWithFilter(
                        @Param("keyword") String keyword,
                        @Param("roleId") Long roleId,
                        @Param("branchId") Long branchId,
                        @Param("permissionCode") String permissionCode,
                        @Param("status") com.zone.agri.entity.enums.UserStatus status,
                        Pageable pageable);

        @Query("SELECT DISTINCT u FROM User u LEFT JOIN u.role r LEFT JOIN r.permissions p WHERE " +
                        "u.role.slug <> 'CUSTOMER' AND " +
                        "(:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.email LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.phoneNumber LIKE CONCAT('%', :keyword, '%') OR u.citizenId LIKE CONCAT('%', :keyword, '%')) AND "
                        +
                        "(:roleId IS NULL OR u.role.id = :roleId) AND " +
                        "(:branchId IS NULL OR u.branch.id = :branchId) AND " +
                        "(:permissionCode IS NULL OR p.code = :permissionCode) AND " +
                        "(:status IS NULL OR u.status = :status)")
        Page<User> findAllEmployeesWithFilter(
                        @Param("keyword") String keyword,
                        @Param("roleId") Long roleId,
                        @Param("branchId") Long branchId,
                        @Param("permissionCode") String permissionCode,
                        @Param("status") com.zone.agri.entity.enums.UserStatus status,
                        Pageable pageable);

        @Query("SELECT u FROM User u WHERE u.role.slug = 'CUSTOMER' " +
                        "AND (:branchId IS NULL OR u.branch.id = :branchId) " +
                        "ORDER BY u.createdAt DESC")
        List<User> findRecentCustomers(@Param("branchId") Long branchId, Pageable pageable);

        @Query("SELECT COUNT(u) FROM User u WHERE u.role.slug = 'CUSTOMER' " +
                        "AND (:branchId IS NULL OR u.branch.id = :branchId)")
        long countCustomers(@Param("branchId") Long branchId);

        // Đếm luỹ kế tính đến 1 thời điểm — dùng để so sánh "Khách hàng" hôm nay với hôm qua.
        @Query("SELECT COUNT(u) FROM User u WHERE u.role.slug = 'CUSTOMER' " +
                        "AND (:branchId IS NULL OR u.branch.id = :branchId) " +
                        "AND u.createdAt <= :endDate")
        long countCustomersBefore(@Param("branchId") Long branchId,
                        @Param("endDate") java.time.LocalDateTime endDate);

        @Query("SELECT COUNT(u) FROM User u WHERE u.role.slug = 'CUSTOMER' " +
                        "AND (:branchId IS NULL OR u.branch.id = :branchId) " +
                        "AND u.status = :status")
        long countCustomersByStatus(
                        @Param("branchId") Long branchId,
                        @Param("status") com.zone.agri.entity.enums.UserStatus status);

        @Query("SELECT COUNT(u) FROM User u WHERE u.role.slug = 'CUSTOMER' " +
                        "AND (:branchId IS NULL OR u.branch.id = :branchId) " +
                        "AND u.createdAt >= :startAt AND u.createdAt < :endAt")
        long countCustomersCreatedBetween(
                        @Param("branchId") Long branchId,
                        @Param("startAt") java.time.LocalDateTime startAt,
                        @Param("endAt") java.time.LocalDateTime endAt);

        boolean existsByEmailAndIdNot(String email, Long id);

        boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

        boolean existsByCitizenIdAndIdNot(String citizenId, Long id);

        @EntityGraph(attributePaths = { "customer" })
        @Query("SELECT u FROM User u WHERE u.role.slug = 'CUSTOMER' " +
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
                        "u.role.slug = 'CUSTOMER' AND " +
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
                        "FROM User u WHERE u.branch.id = :branchId AND u.role.slug = :slug ORDER BY u.fullName")
        List<Map<String, Object>> findByBranchIdAndRole(@Param("branchId") Long branchId, @Param("slug") String slug);

        // 🟢 Notification recipient resolution: users holding a given permission, scoped to a branch
        // (or system-wide when branchId is null — same convention as WarehouseContext.isSuperAdmin()).
        @Query("SELECT DISTINCT u FROM User u JOIN u.role r JOIN r.permissions p WHERE " +
                        "p.code = :permissionCode AND u.status = com.zone.agri.entity.enums.UserStatus.ACTIVE AND " +
                        "((:branchId IS NULL AND u.branch IS NULL) OR (:branchId IS NOT NULL AND u.branch.id = :branchId))")
        List<User> findUsersByPermissionCodeAndBranch(@Param("permissionCode") String permissionCode,
                        @Param("branchId") Long branchId);

        @Query("SELECT DISTINCT u FROM User u LEFT JOIN u.role r LEFT JOIN r.permissions p WHERE " +
                        "u.email IS NOT NULL AND u.email <> '' AND " +
                        "u.status = com.zone.agri.entity.enums.UserStatus.ACTIVE AND " +
                        "(r.slug = :roleSlug OR p.code = :permissionCode) " +
                        "ORDER BY u.id ASC")
        List<User> findActiveUsersByRoleSlugOrPermissionCode(
                        @Param("roleSlug") String roleSlug,
                        @Param("permissionCode") String permissionCode);
}

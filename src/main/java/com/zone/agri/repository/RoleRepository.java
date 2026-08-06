package com.zone.agri.repository;

import com.zone.agri.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findBySlug(String slug);

    // Truy vấn tìm kiếm và lọc nâng cao
    @Query("SELECT r FROM Role r WHERE " +
           "(:keyword IS NULL OR LOWER(r.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:isSystem IS NULL OR r.isSystem = :isSystem) AND " +
           "(:isActive IS NULL OR r.isActive = :isActive)")
    org.springframework.data.domain.Page<Role> findAllWithFilter(
            @Param("keyword") String keyword,
            @Param("isSystem") Boolean isSystem,
            @Param("isActive") Boolean isActive,
            org.springframework.data.domain.Pageable pageable);

    // Kiểm tra slug đã tồn tại ở bản ghi khác (loại trừ ID hiện tại)
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Role r WHERE r.slug = :slug AND r.id != :id")
    boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("id") Long id);

    // Kiểm tra slug tồn tại (cho trường hợp tạo mới)
    boolean existsBySlug(String slug);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Role r WHERE LOWER(r.displayName) = LOWER(:displayName) AND r.id != :id")
    boolean existsByDisplayNameIgnoreCaseAndIdNot(@Param("displayName") String displayName, @Param("id") Long id);

    // Đếm số user đang sử dụng role này
    @Query("SELECT COUNT(u) FROM User u WHERE u.role.id = :roleId")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT u.role.id, COUNT(u) FROM User u WHERE u.role.id IN :roleIds GROUP BY u.role.id")
    List<Object[]> countUsersByRoleIds(@Param("roleIds") List<Long> roleIds);
}

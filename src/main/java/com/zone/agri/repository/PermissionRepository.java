package com.zone.agri.repository;

import com.zone.agri.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    java.util.Set<Permission> findAllByCodeIn(java.util.List<String> codes);
}
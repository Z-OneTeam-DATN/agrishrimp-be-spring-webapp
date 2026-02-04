package com.zone.agri.repository;

import com.zone.agri.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> { // <--- SỬA STRING THÀNH LONG
    Optional<Role> findBySlug(String slug);
}
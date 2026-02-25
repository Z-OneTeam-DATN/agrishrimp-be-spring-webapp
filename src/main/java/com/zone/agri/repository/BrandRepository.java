package com.zone.agri.repository;

import com.zone.agri.entity.Brand;
import com.zone.agri.entity.enums.BrandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByName(String name);
    List<Brand> findByStatus(BrandStatus status);
}
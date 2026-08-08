package com.zone.agri.repository;

import com.zone.agri.entity.Brand;
import com.zone.agri.entity.enums.BrandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByName(String name);
    List<Brand> findByStatus(BrandStatus status);
    List<Brand> findByStatusOrderByIdDesc(BrandStatus status);
    List<Brand> findByNameContainingIgnoreCaseOrderByIdDesc(String keyword);
    List<Brand> findByNameContainingIgnoreCaseAndStatusOrderByIdDesc(String keyword, BrandStatus status);
    List<Brand> findAllByOrderByIdDesc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}

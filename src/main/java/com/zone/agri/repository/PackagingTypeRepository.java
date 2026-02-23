package com.zone.agri.repository;

import com.zone.agri.entity.PackagingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PackagingTypeRepository extends JpaRepository<PackagingType, Long> {
}
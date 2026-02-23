package com.zone.agri.repository;

import com.zone.agri.entity.UnitConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {
}

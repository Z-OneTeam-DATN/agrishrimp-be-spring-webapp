package com.zone.agri.repository;

import com.zone.agri.entity.Attribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributeRepository extends JpaRepository<Attribute, Long> {
    @Query("""
            SELECT DISTINCT a FROM Attribute a
            LEFT JOIN FETCH a.attributeValues
            WHERE a.status IS NULL OR a.status = com.zone.agri.entity.enums.AttributeStatus.ACTIVE
            ORDER BY a.id ASC
            """)
    List<Attribute> findPublicAttributesWithValues();

    Optional<Attribute> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<Attribute> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}

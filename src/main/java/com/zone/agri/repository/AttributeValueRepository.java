package com.zone.agri.repository;

import com.zone.agri.entity.Attribute;
import com.zone.agri.entity.AttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributeValueRepository extends JpaRepository<AttributeValue, Long> {
    List<AttributeValue> findByAttributeId(Long attributeId);
}

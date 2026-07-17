package com.zone.agri.repository;

import com.zone.agri.entity.AttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttributeValueRepository extends JpaRepository<AttributeValue, Long> {
    List<AttributeValue> findByAttributeId(Long attributeId);

    @Query("select av from AttributeValue av join fetch av.attribute a where (:attributeId is null or a.id <> :attributeId)")
    List<AttributeValue> findAllWithAttributeExcludingAttributeId(@Param("attributeId") Long attributeId);
}

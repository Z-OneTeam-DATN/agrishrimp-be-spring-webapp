package com.zone.agri.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zone.agri.entity.SKUAttributeValue;

@Repository
public interface SKUAttributeValueRepository extends JpaRepository<SKUAttributeValue, Long> {
    List<SKUAttributeValue> findBySkuId(Long skuId);

    @Query("""
        SELECT sav
        FROM SKUAttributeValue sav
        JOIN FETCH sav.attribute
        JOIN FETCH sav.attributeValue
        WHERE sav.sku.id IN :skuIds
    """)
    List<SKUAttributeValue> findBySkuIdIn(@Param("skuIds") List<Long> skuIds);

    void deleteBySkuId(Long skuId);

    boolean existsByAttributeId(Long attributeId);

    @Query("SELECT COUNT(s) > 0 FROM SKUAttributeValue s WHERE s.attributeValue.id = ?1")
    boolean existsByAttributeValueId(Long attributeValueId);
}

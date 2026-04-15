package com.zone.agri.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zone.agri.entity.SKUAttributeValue;

@Repository
public interface SKUAttributeValueRepository extends JpaRepository<SKUAttributeValue, Long> {
    List<SKUAttributeValue> findBySkuId(Long skuId);

    List<SKUAttributeValue> findBySkuIdIn(List<Long> skuIds);

    void deleteBySkuId(Long skuId);

    boolean existsByAttributeId(Long attributeId);

    boolean existsByAttributeValueId(Long attributeValueId);
}

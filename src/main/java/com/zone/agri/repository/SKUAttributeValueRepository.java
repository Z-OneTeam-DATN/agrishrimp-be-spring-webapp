package com.zone.agri.repository;

import com.zone.agri.entity.SKUAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SKUAttributeValueRepository extends JpaRepository<SKUAttributeValue, Long> {
    List<SKUAttributeValue> findBySkuId(Long skuId);
    void deleteBySkuId(Long skuId);
}

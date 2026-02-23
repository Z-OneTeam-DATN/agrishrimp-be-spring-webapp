package com.zone.agri.repository;

import com.zone.agri.entity.ProductForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductFormRepository extends JpaRepository<ProductForm, Long> {
}
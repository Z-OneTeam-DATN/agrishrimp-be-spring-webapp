package com.zone.agri.repository;

import com.zone.agri.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {


    // Tìm tất cả danh mục cha (parent = null)
    List<Category> findByParentIsNull();

    //Tìm danh mục theo trạng thái ACTIVE
    // List<Category> findByStatus(CategoryStatus status);
}
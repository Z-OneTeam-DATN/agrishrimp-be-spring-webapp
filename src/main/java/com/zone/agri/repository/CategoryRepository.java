package com.zone.agri.repository;

import com.zone.agri.entity.Category;
import com.zone.agri.entity.enums.CategoryStatus;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Kiểm tra trùng tên (Bỏ qua hoa thường)
    boolean existsByNameIgnoreCase(String name);

    // Kiểm tra trùng tên khi cập nhật (Trừ chính nó)
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // Tìm tất cả danh mục cha (parent = null)
    List<Category> findByParentIsNull();

    //Tìm danh mục theo trạng thái ACTIVE
    List<Category> findByStatus(CategoryStatus status);



    // API Tìm kiếm và lọc danh mục
    @Query("SELECT c FROM Category c WHERE " +
            "(:keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:status IS NULL OR c.status = :status)")
    List<Category> searchCategories(@Param("keyword") String keyword, @Param("status") CategoryStatus status);

}
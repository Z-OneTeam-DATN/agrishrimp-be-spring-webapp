package com.zone.agri.repository;

import com.zone.agri.entity.BlogCategory;
import com.zone.agri.entity.enums.BlogCategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlogCategoryRepository extends JpaRepository<BlogCategory, Long> {
    Optional<BlogCategory> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsByName(String name);
    Optional<BlogCategory> findBySlugIgnoreCase(String slug);
    List<BlogCategory> findAllByOrderByIdAsc();
    List<BlogCategory> findByStatusOrderByIdAsc(BlogCategoryStatus status);
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}

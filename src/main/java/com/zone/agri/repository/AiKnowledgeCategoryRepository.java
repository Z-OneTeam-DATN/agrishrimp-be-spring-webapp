package com.zone.agri.repository;

import com.zone.agri.entity.AiKnowledgeCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiKnowledgeCategoryRepository extends JpaRepository<AiKnowledgeCategory, Long> {
    Optional<AiKnowledgeCategory> findBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    boolean existsBySlug(String slug);
    List<AiKnowledgeCategory> findAllByEnabledTrueOrderBySortOrderAscNameAsc();
}

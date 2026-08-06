package com.zone.agri.repository;

import com.zone.agri.entity.AiKnowledgeReviewCase;
import com.zone.agri.entity.enums.AiReviewCaseStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiKnowledgeReviewCaseRepository extends JpaRepository<AiKnowledgeReviewCase, Long> {
    List<AiKnowledgeReviewCase> findTop100ByOrderByCreatedAtDesc();
    List<AiKnowledgeReviewCase> findTop100ByStatusOrderByCreatedAtDesc(AiReviewCaseStatus status);
}

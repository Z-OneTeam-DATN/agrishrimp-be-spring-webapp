package com.zone.agri.repository;

import com.zone.agri.entity.AiKeywordAnswerSet;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiKeywordAnswerSetRepository extends JpaRepository<AiKeywordAnswerSet, Long> {
    Optional<AiKeywordAnswerSet> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    List<AiKeywordAnswerSet> findAllByStatusAndEnabledTrueOrderByPriorityDescNameAsc(AiKnowledgeStatus status);
}

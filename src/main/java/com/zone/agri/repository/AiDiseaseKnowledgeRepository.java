package com.zone.agri.repository;

import com.zone.agri.entity.AiDiseaseKnowledge;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiDiseaseKnowledgeRepository extends JpaRepository<AiDiseaseKnowledge, Long> {
    Optional<AiDiseaseKnowledge> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    List<AiDiseaseKnowledge> findAllByStatusAndEnabledTrueOrderByPriorityDescNameViAsc(AiKnowledgeStatus status);
}

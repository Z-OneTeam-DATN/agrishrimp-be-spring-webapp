package com.zone.agri.repository;

import com.zone.agri.entity.AiKeywordAnswerSet;
import com.zone.agri.entity.enums.AiKnowledgeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiKeywordAnswerSetRepository extends JpaRepository<AiKeywordAnswerSet, Long> {
    Optional<AiKeywordAnswerSet> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);

    // LEFT JOIN FETCH category: xem giai thich chi tiet o AiDiseaseKnowledgeRepository — cung bi
    // cache lai trong approvedSnapshotRef nen category phai duoc nap san, khong de lazy.
    @Query("SELECT k FROM AiKeywordAnswerSet k LEFT JOIN FETCH k.category "
            + "WHERE k.status = :status AND k.enabled = true "
            + "ORDER BY k.priority DESC, k.name ASC")
    List<AiKeywordAnswerSet> findAllByStatusAndEnabledTrueOrderByPriorityDescNameAsc(@Param("status") AiKnowledgeStatus status);
}

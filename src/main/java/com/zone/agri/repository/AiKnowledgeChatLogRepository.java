package com.zone.agri.repository;

import com.zone.agri.entity.AiKnowledgeChatLog;
import com.zone.agri.entity.enums.AiKnowledgeMatchType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiKnowledgeChatLogRepository extends JpaRepository<AiKnowledgeChatLog, Long> {
    @Query("""
        select l.questionText, count(l)
        from AiKnowledgeChatLog l
        where l.matched = false
        group by l.questionText
        order by count(l) desc
    """)
    List<Object[]> findTopUnmatchedQuestions();

    long countByMatched(boolean matched);

    long countByMatchedType(AiKnowledgeMatchType matchedType);
}

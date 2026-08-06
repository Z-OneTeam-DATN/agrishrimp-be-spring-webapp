package com.zone.agri.repository;

import com.zone.agri.entity.AiKnowledgeChatLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiKnowledgeChatLogRepository extends JpaRepository<AiKnowledgeChatLog, Long> {

    /**
     * Chi lay cot createdAt (khong lay full entity) de group theo ngay o tang Java — tranh dung
     * JPQL FUNCTION('DATE', ...) group-by chua co tien le trong codebase, rui ro khac biet cu phap
     * giua H2 (test) va MySQL (prod).
     */
    @Query("SELECT c.createdAt FROM AiKnowledgeChatLog c "
            + "WHERE c.userId = :userId AND c.sourceChannel = :sourceChannel AND c.createdAt >= :since "
            + "ORDER BY c.createdAt DESC")
    List<LocalDateTime> findCreatedAtByUserIdAndSourceChannelSince(
            @Param("userId") Long userId, @Param("sourceChannel") String sourceChannel, @Param("since") LocalDateTime since);

    List<AiKnowledgeChatLog> findByUserIdAndSourceChannelAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Long userId, String sourceChannel, LocalDateTime startOfDay, LocalDateTime endOfDay);
}

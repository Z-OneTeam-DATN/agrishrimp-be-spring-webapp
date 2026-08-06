package com.zone.agri.repository;

import com.zone.agri.entity.AiChatClarifySession;
import com.zone.agri.entity.enums.AiClarifySessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiChatClarifySessionRepository extends JpaRepository<AiChatClarifySession, Long> {

    /**
     * Một sessionId chat có thể có nhiều phiên clarify theo thời gian (mỗi lần chốt/escalate xong,
     * người dùng hỏi tiếp một triệu chứng mới sẽ mở phiên mới) — chỉ tra cứu phiên đang ACTIVE.
     */
    Optional<AiChatClarifySession> findFirstBySessionIdAndStatusOrderByIdDesc(String sessionId, AiClarifySessionStatus status);
}

package com.zone.agri.repository;

import com.zone.agri.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByCustomerId(Long customerId);

    @Query("SELECT c FROM Conversation c ORDER BY CASE WHEN c.lastMessageAt IS NULL THEN 0 ELSE 1 END DESC, c.lastMessageAt DESC")
    Page<Conversation> findAllOrderByLastMessageDesc(Pageable pageable);

    @Query("SELECT c FROM Conversation c WHERE c.unreadByShop > 0 ORDER BY c.lastMessageAt DESC")
    Page<Conversation> findUnreadByShop(Pageable pageable);
}

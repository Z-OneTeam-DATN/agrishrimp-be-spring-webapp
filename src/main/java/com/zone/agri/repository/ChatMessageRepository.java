package com.zone.agri.repository;

import com.zone.agri.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :convId ORDER BY m.createdAt ASC")
    List<ChatMessage> findByConversationId(@Param("convId") Long conversationId);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.conversation.id = :convId AND m.sender.id <> :readerId AND m.isRead = false")
    void markAsReadByConversationAndNotSender(@Param("convId") Long conversationId, @Param("readerId") Long readerId);

    long countByConversationIdAndIsReadFalseAndSenderIdNot(Long conversationId, Long senderId);
}

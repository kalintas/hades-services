package com.hades.services.repository;

import com.hades.services.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionIdOrderByTimestampAsc(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}

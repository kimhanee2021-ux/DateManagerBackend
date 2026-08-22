package org.ict.datemanagerbackend.domain.aichat.repository;

import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessageIntent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatMessageIntentRepository extends JpaRepository<AiChatMessageIntent, Long> {
}

package com.shareride.notification.repository;

import com.shareride.common.outbox.OutboxMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for managing transactional outbox messages in Notification Service. */
@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {}

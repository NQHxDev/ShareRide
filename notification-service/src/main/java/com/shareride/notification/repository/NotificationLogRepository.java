package com.shareride.notification.repository;

import com.shareride.notification.domain.NotificationLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Optional<NotificationLog> findByEventId(UUID eventId);

    List<NotificationLog> findByRecipientEmail(String recipientEmail);
}

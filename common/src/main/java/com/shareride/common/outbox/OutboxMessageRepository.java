package com.shareride.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

   List<OutboxMessage> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

}

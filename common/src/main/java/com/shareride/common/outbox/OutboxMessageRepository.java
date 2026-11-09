package com.shareride.common.outbox;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxMessage> findByStatusInOrderByCreatedAtAsc(
            Collection<OutboxStatus> statuses, Pageable pageable);

    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Modifying
    @Query(
            "UPDATE OutboxMessage m SET m.status = :newStatus, m.retryCount = 0, m.errorMessage ="
                    + " null, m.processedAt = null WHERE m.id = :id")
    int resetStatusById(@Param("id") UUID id, @Param("newStatus") OutboxStatus newStatus);

    @Modifying
    @Query(
            "UPDATE OutboxMessage m SET m.status = :newStatus, m.retryCount = 0, m.errorMessage ="
                    + " null, m.processedAt = null WHERE m.status = :targetStatus")
    int resetStatusByTargetStatus(
            @Param("targetStatus") OutboxStatus targetStatus,
            @Param("newStatus") OutboxStatus newStatus);
}

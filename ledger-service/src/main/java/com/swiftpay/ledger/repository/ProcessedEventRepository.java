package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.entity.ProcessedEvent;
import com.swiftpay.ledger.inbox.ProcessedEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link ProcessedEvent}.
 *
 * <p>The duplicate-detection contract is implemented at the database level
 * via the {@code uq_inbox_event_group} unique constraint, not at the
 * application level — even a race between two consumer pods is safe.</p>
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    Optional<ProcessedEvent> findByEventIdAndConsumerGroup(UUID eventId, String consumerGroup);

    @Modifying
    @Query("UPDATE ProcessedEvent p SET p.status = :status, p.processedAt = :processedAt, p.lastError = :lastError " +
            "WHERE p.eventId = :eventId AND p.consumerGroup = :consumerGroup")
    int updateStatus(@Param("eventId") UUID eventId,
                     @Param("consumerGroup") String consumerGroup,
                     @Param("status") ProcessedEventStatus status,
                     @Param("processedAt") OffsetDateTime processedAt,
                     @Param("lastError") String lastError);
}
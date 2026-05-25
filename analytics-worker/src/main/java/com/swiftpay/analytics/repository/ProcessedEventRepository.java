package com.swiftpay.analytics.repository;

import com.swiftpay.analytics.entity.ProcessedEvent;
import com.swiftpay.analytics.inbox.ProcessedEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

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
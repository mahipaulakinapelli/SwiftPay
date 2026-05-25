package com.swiftpay.ledger.entity;

import com.swiftpay.ledger.inbox.ProcessedEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumer-side inbox row: one record per {@code (event_id, consumer_group)}
 * pair this service has attempted to process.
 *
 * <p>The unique constraint on those two columns is what makes redelivery
 * idempotent — a second consume of the same Kafka event fails the INSERT,
 * and the consumer treats that as "already handled, skip".</p>
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Producer-assigned event id (carried in every {@code com.swiftpay.ledger.event.*} record). */
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Kafka consumer group; lets the same event be processed independently by different services. */
    @Column(name = "consumer_group", nullable = false, length = 80)
    private String consumerGroup;

    @Column(name = "topic", nullable = false, length = 160)
    private String topic;

    @Column(name = "partition", nullable = false)
    private Integer partition;

    @Column(name = "offset_value", nullable = false)
    private Long offsetValue;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** Optional SHA-256 of the payload — useful for poison-message detection. */
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ProcessedEventStatus status;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
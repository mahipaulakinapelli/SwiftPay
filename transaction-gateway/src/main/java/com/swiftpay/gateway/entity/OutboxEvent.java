package com.swiftpay.gateway.entity;

import com.swiftpay.gateway.outbox.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Durable record of a domain event that must reach Kafka.
 *
 * <p>The business transaction inserts this row in the same DB commit that
 * mutates the aggregate; a separate scheduled relay then drains rows to the
 * broker. A row never leaves {@link OutboxStatus#PENDING} until the broker
 * acks the publish, which is what removes the dual-write window.</p>
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Aggregate root the event belongs to, e.g. {@code Payment}. Useful for ops queries. */
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    /** Stringified id of the aggregate (often a UUID, sometimes composite). */
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** Fully-qualified class name of the event payload — used by the relay to deserialize. */
    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    /** Target Kafka topic for this event. */
    @Column(name = "topic", nullable = false, length = 160)
    private String topic;

    /** Serialized event payload (snake_case JSON, persisted as JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    /** Wall-clock of the most recent publish attempt; used by the publisher's exponential-backoff filter. */
    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;
}
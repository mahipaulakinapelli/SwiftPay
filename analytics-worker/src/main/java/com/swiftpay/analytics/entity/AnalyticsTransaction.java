package com.swiftpay.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row in the analytics read-model: a flattened projection of a
 * {@code PaymentCompletedEvent}.
 *
 * <p>The {@code UNIQUE(transaction_id)} constraint enforces idempotent
 * replay: re-consuming the topic from {@code earliest} is safe — duplicate
 * inserts are rejected by the DB and skipped by the application.</p>
 */
@Entity
@Table(
    name = "analytics_transactions",
    uniqueConstraints = @UniqueConstraint(name = "uq_an_tx_id", columnNames = "transaction_id")
)
@Getter
@Setter
@NoArgsConstructor
public class AnalyticsTransaction {

    /** Surrogate id — separate from {@code transaction_id} so insert order is preserved as a tie-breaker. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cross-service transaction id (same UUID present in gateway DB, ledger DB, Kafka events). */
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Sender's balance after the transfer (denormalised for reporting). */
    @Column(name = "sender_balance_after", precision = 19, scale = 4)
    private BigDecimal senderBalanceAfter;

    /** Receiver's balance after the transfer (denormalised for reporting). */
    @Column(name = "receiver_balance_after", precision = 19, scale = 4)
    private BigDecimal receiverBalanceAfter;

    /** Originating event id — links a projection row back to the Kafka record. */
    @Column(name = "event_id")
    private UUID eventId;

    /** Producer-side timestamp from the event payload. */
    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    /** Server-side time at which this projection row was recorded. */
    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;
}
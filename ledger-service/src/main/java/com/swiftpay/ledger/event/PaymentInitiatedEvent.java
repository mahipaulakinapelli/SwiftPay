package com.swiftpay.ledger.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftpay.ledger.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code payment-initiated} — consumed by {@code ledger-service}.
 *
 * <p>Schema source of truth lives in {@code transaction-gateway}.
 * This is the local deserialization-side copy; keep field names + JSON
 * property mappings in sync with the producer.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentInitiatedEvent(
        UUID eventId,
        Integer eventVersion,
        OffsetDateTime occurredAt,
        UUID correlationId,
        String producer,
        UUID transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String currency,
        TransactionStatus status
) {

    public static final int CURRENT_VERSION = 1;
    /** Convenience tag for events synthesized inside the ledger (e.g. tests). */
    public static final String PRODUCER_GATEWAY = "transaction-gateway";

    @JsonCreator
    public PaymentInitiatedEvent(
            @JsonProperty("event_id") UUID eventId,
            @JsonProperty("event_version") Integer eventVersion,
            @JsonProperty("occurred_at") OffsetDateTime occurredAt,
            @JsonProperty("correlation_id") UUID correlationId,
            @JsonProperty("producer") String producer,
            @JsonProperty("transaction_id") UUID transactionId,
            @JsonProperty("sender_id") Long senderId,
            @JsonProperty("receiver_id") Long receiverId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") TransactionStatus status
    ) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID();
        this.eventVersion = eventVersion != null ? eventVersion : CURRENT_VERSION;
        this.occurredAt = occurredAt != null ? occurredAt : OffsetDateTime.now();
        this.correlationId = correlationId != null ? correlationId : this.eventId;
        this.producer = producer;
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
    }

    /**
     * Synthesize an in-memory event with envelope defaults — used by tests and
     * any local code path that builds the event without going through Kafka.
     * Not part of the wire contract.
     */
    public static PaymentInitiatedEvent of(
            UUID transactionId,
            Long senderId,
            Long receiverId,
            java.math.BigDecimal amount,
            String currency,
            TransactionStatus status
    ) {
        UUID eventId = UUID.randomUUID();
        return new PaymentInitiatedEvent(
                eventId, CURRENT_VERSION, OffsetDateTime.now(), eventId, PRODUCER_GATEWAY,
                transactionId, senderId, receiverId, amount, currency, status
        );
    }
}
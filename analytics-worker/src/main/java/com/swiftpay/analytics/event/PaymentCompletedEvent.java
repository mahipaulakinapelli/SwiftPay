package com.swiftpay.analytics.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code payment-completed} — consumed by {@code analytics-worker}.
 *
 * <p>Schema source of truth lives in {@code ledger-service}. This is the local
 * deserialization-side copy.</p>
 *
 * <p>{@code status} is unused by analytics, so it is decoded as {@code String}
 * here to avoid pulling in a TransactionStatus enum that analytics doesn't
 * otherwise need.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedEvent(
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
        BigDecimal senderBalanceAfter,
        BigDecimal receiverBalanceAfter,
        String status
) {

    public static final int CURRENT_VERSION = 1;

    @JsonCreator
    public PaymentCompletedEvent(
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
            @JsonProperty("sender_balance_after") BigDecimal senderBalanceAfter,
            @JsonProperty("receiver_balance_after") BigDecimal receiverBalanceAfter,
            @JsonProperty("status") String status
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
        this.senderBalanceAfter = senderBalanceAfter;
        this.receiverBalanceAfter = receiverBalanceAfter;
        this.status = status;
    }

    /**
     * Synthesize an in-memory event for tests / local code paths that don't
     * round-trip through Kafka. Sets {@code status="COMPLETED"} unconditionally.
     */
    public static PaymentCompletedEvent of(
            UUID transactionId,
            Long senderId,
            Long receiverId,
            java.math.BigDecimal amount,
            String currency,
            java.math.BigDecimal senderBalanceAfter,
            java.math.BigDecimal receiverBalanceAfter
    ) {
        UUID eventId = UUID.randomUUID();
        return new PaymentCompletedEvent(
                eventId, CURRENT_VERSION, OffsetDateTime.now(), eventId, "ledger-service",
                transactionId, senderId, receiverId, amount, currency,
                senderBalanceAfter, receiverBalanceAfter, "COMPLETED"
        );
    }
}
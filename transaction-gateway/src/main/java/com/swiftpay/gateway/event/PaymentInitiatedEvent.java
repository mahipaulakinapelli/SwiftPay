package com.swiftpay.gateway.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftpay.gateway.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code payment-initiated} — owned and produced by {@code transaction-gateway}.
 *
 * <p>This class is the source of truth for the topic's wire schema. Other services
 * may keep their own deserialization-side copy, but changes to the contract live
 * in this file in this repo.</p>
 *
 * <p>Forward/backward compatibility:
 * <ul>
 *   <li>{@code @JsonIgnoreProperties(ignoreUnknown = true)} — new fields added by future producers don't break older consumers.</li>
 *   <li>{@code @JsonCreator} backfills envelope fields when older payloads arrive.</li>
 * </ul>
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
        this.producer = producer != null ? producer : PRODUCER_GATEWAY;
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
    }

    public static PaymentInitiatedEvent of(
            UUID transactionId,
            Long senderId,
            Long receiverId,
            BigDecimal amount,
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
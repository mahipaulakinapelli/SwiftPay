package com.swiftpay.ledger.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftpay.ledger.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code payment-completed} — owned and produced by {@code ledger-service}.
 *
 * <p>Schema source of truth lives in this file in this repo.</p>
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
        TransactionStatus status
) {

    public static final int CURRENT_VERSION = 1;
    public static final String PRODUCER_LEDGER = "ledger-service";

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
            @JsonProperty("status") TransactionStatus status
    ) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID();
        this.eventVersion = eventVersion != null ? eventVersion : CURRENT_VERSION;
        this.occurredAt = occurredAt != null ? occurredAt : OffsetDateTime.now();
        this.correlationId = correlationId != null ? correlationId : this.eventId;
        this.producer = producer != null ? producer : PRODUCER_LEDGER;
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amount = amount;
        this.currency = currency;
        this.senderBalanceAfter = senderBalanceAfter;
        this.receiverBalanceAfter = receiverBalanceAfter;
        this.status = status;
    }

    public static PaymentCompletedEvent of(
            UUID transactionId,
            Long senderId,
            Long receiverId,
            BigDecimal amount,
            String currency,
            BigDecimal senderBalanceAfter,
            BigDecimal receiverBalanceAfter
    ) {
        return of(transactionId, senderId, receiverId, amount, currency,
                senderBalanceAfter, receiverBalanceAfter, null);
    }

    public static PaymentCompletedEvent of(
            UUID transactionId,
            Long senderId,
            Long receiverId,
            BigDecimal amount,
            String currency,
            BigDecimal senderBalanceAfter,
            BigDecimal receiverBalanceAfter,
            UUID correlationId
    ) {
        UUID eventId = UUID.randomUUID();
        return new PaymentCompletedEvent(
                eventId, CURRENT_VERSION, OffsetDateTime.now(),
                correlationId != null ? correlationId : eventId, PRODUCER_LEDGER,
                transactionId, senderId, receiverId, amount, currency,
                senderBalanceAfter, receiverBalanceAfter,
                TransactionStatus.COMPLETED
        );
    }
}
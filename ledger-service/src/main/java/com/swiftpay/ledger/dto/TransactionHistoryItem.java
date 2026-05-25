package com.swiftpay.ledger.dto;

import com.swiftpay.ledger.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of a user's transaction history — returned by
 * {@code GET /v1/transactions/{userId}}.
 *
 * @param transactionId cross-service transaction id
 * @param senderId      payer
 * @param receiverId    payee
 * @param amount        amount transferred
 * @param currency      ISO-4217 currency
 * @param status        final lifecycle status (typically {@link TransactionStatus#COMPLETED} or {@code FAILED})
 * @param createdAt     ledger-side creation timestamp
 * @param updatedAt     ledger-side last-update timestamp (status transition)
 */
@Schema(description = "One row of a user's transaction history")
public record TransactionHistoryItem(

        @Schema(description = "Transaction UUID")
        UUID transactionId,

        @Schema(description = "Sender user id")
        Long senderId,

        @Schema(description = "Receiver user id")
        Long receiverId,

        @Schema(description = "Amount transferred")
        BigDecimal amount,

        @Schema(description = "ISO-4217 currency code")
        String currency,

        @Schema(description = "Final transaction status")
        TransactionStatus status,

        @Schema(description = "Server creation timestamp")
        OffsetDateTime createdAt,

        @Schema(description = "Last update timestamp")
        OffsetDateTime updatedAt
) {}
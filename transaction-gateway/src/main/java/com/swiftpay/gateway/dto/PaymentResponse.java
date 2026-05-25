package com.swiftpay.gateway.dto;

import com.swiftpay.gateway.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Outbound payload for {@code POST /v1/payments}.
 *
 * <p>{@code status} is always {@link TransactionStatus#PENDING} on the
 * gateway side — terminal settlement happens in the ledger.</p>
 *
 * @param transactionId echoed from the request
 * @param senderId      echoed from the request
 * @param receiverId    echoed from the request
 * @param amount        echoed from the request
 * @param currency      echoed from the request
 * @param status        always {@link TransactionStatus#PENDING}
 * @param acceptedAt    server-side acceptance timestamp (== row's {@code createdAt})
 */
@Schema(description = "Payment initiation response")
public record PaymentResponse(

        @Schema(description = "Transaction UUID")
        UUID transactionId,

        @Schema(description = "Sender user ID")
        Long senderId,

        @Schema(description = "Receiver user ID")
        Long receiverId,

        @Schema(description = "Amount accepted")
        BigDecimal amount,

        @Schema(description = "ISO-4217 currency code")
        String currency,

        @Schema(description = "Current transaction status")
        TransactionStatus status,

        @Schema(description = "Server-side acceptance timestamp")
        OffsetDateTime acceptedAt
) {}
package com.swiftpay.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound payload for {@code POST /v1/payments}.
 *
 * <p>Bean Validation guards the field-level invariants before any service
 * code runs; the global exception handler turns a violation into a
 * {@code 400 VALIDATION_ERROR} response with per-field detail.</p>
 *
 * @param senderId      paying user id (positive)
 * @param receiverId    receiving user id (positive)
 * @param amount        amount to transfer (≥ 0.01, up to 4 decimal places)
 * @param currency      ISO-4217 3-letter uppercase code
 * @param transactionId client-supplied UUID (used both as PK and idempotency key)
 */
@Schema(description = "Payment initiation request")
public record PaymentRequest(

        @Schema(description = "Sender user ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "sender_id is required")
        @Positive(message = "sender_id must be positive")
        Long senderId,

        @Schema(description = "Receiver user ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "receiver_id is required")
        @Positive(message = "receiver_id must be positive")
        Long receiverId,

        @Schema(description = "Amount to transfer", example = "100.50", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 15, fraction = 4, message = "amount has at most 15 integer and 4 fraction digits")
        BigDecimal amount,

        @Schema(description = "ISO-4217 currency code", example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter uppercase ISO code")
        String currency,

        @Schema(description = "Client-supplied transaction UUID (also used as idempotency key)",
                example = "a7e2c8a0-3c2d-4e7b-9f1a-b6c3d2e1f0a5",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "transaction_id is required")
        UUID transactionId
) {}
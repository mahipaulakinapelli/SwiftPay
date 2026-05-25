package com.swiftpay.ledger.dto;

import com.swiftpay.ledger.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * One row in a user's balance response — returned by
 * {@code GET /v1/accounts/{userId}/balances}.
 *
 * <p>One entry per (user × currency) account.</p>
 *
 * @param accountId surrogate account id
 * @param currency  ISO-4217 currency code
 * @param balance   current balance
 * @param status    account lifecycle status
 */
@Schema(description = "One account balance for a user")
public record BalanceDto(
        @Schema(description = "Account id")
        Long accountId,

        @Schema(description = "ISO-4217 currency code")
        String currency,

        @Schema(description = "Current balance")
        BigDecimal balance,

        @Schema(description = "Account status")
        AccountStatus status
) {}
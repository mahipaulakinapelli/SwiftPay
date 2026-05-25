package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.response.ApiResponse;
import com.swiftpay.ledger.cache.BalanceCacheService;
import com.swiftpay.ledger.dto.BalanceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-side balance API.
 *
 * <p>Goes through {@link BalanceCacheService} for cache-aside behavior:
 * first request loads from Postgres and fills Redis, subsequent requests
 * hit the cache, transfers invalidate it. See {@code architecture.md}.</p>
 */
@RestController
@RequestMapping("/v1/accounts")
@Validated
@Tag(name = "Accounts", description = "Account balance endpoints")
public class AccountController {

    private final BalanceCacheService balanceCacheService;

    /** Constructor-based dependency injection. */
    public AccountController(BalanceCacheService balanceCacheService) {
        this.balanceCacheService = balanceCacheService;
    }

    /**
     * Get all balances for a user, one row per (user × currency) account.
     *
     * @param userId target user; must be positive
     */
    @GetMapping("/{userId}/balances")
    @Operation(summary = "Get all balances for a user (cache-aside via Redis)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Balances"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid userId")
    })
    public ResponseEntity<ApiResponse<List<BalanceDto>>> balances(
            @Parameter(description = "User id (positive integer)", example = "1", required = true)
            @PathVariable
            @Min(value = 1, message = "userId must be positive")
            Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(balanceCacheService.getBalances(userId)));
    }
}
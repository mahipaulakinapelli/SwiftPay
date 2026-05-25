package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.response.ApiResponse;
import com.swiftpay.ledger.service.FailureInjector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Test-only HTTP surface for arming the {@link FailureInjector}.
 *
 * <p>In a production build this controller would be guarded behind a profile
 * or feature flag. It exists to keep retry / DLT demos deterministic without
 * having to take the database down.</p>
 */
@RestController
@RequestMapping("/debug")
@Validated
@Tag(name = "Debug", description = "Test-only endpoints (failure injection)")
public class DebugController {

    private final FailureInjector failureInjector;

    /** Constructor-based dependency injection. */
    public DebugController(FailureInjector failureInjector) {
        this.failureInjector = failureInjector;
    }

    /**
     * Arm the failure injector for a specific {@code transaction_id}.
     *
     * @param transactionId the id the injector will throw transient errors for
     * @param attempts      number of subsequent processing attempts to fail (default 2)
     */
    @PostMapping("/fail-tx/{transactionId}")
    @Operation(summary = "Arm the failure injector for a transaction_id")
    public ResponseEntity<ApiResponse<Map<String, Object>>> armFailure(
            @PathVariable UUID transactionId,
            @RequestParam(defaultValue = "2") @Min(value = 1, message = "attempts must be >= 1") int attempts
    ) {
        failureInjector.arm(transactionId, attempts);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("transaction_id", transactionId, "attempts_to_fail", attempts)
        ));
    }
}
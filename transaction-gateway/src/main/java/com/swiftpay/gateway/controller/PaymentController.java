package com.swiftpay.gateway.controller;

import com.swiftpay.gateway.response.ApiResponse;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP edge for payment requests.
 *
 * <p>This controller is intentionally thin — it validates the request and
 * delegates everything else to {@link PaymentService}. The transactional and
 * eventing concerns live in the service layer.</p>
 *
 * <p>Returns {@code 202 Accepted} (not {@code 200}/{@code 201}) because settlement
 * is asynchronous: the gateway has accepted the request and forwarded it to the
 * ledger via Kafka, but the terminal status is decided downstream.</p>
 */
@RestController
@RequestMapping("/v1/payments")
@Tag(name = "Payments", description = "Payment initiation endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    /** Constructor-based dependency injection. */
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Accept a new payment.
     *
     * @param request validated payment input (sender, receiver, amount, currency, client-supplied transaction id)
     * @return {@code 202 Accepted} with the persisted record in {@code PENDING} state
     *
     * @apiNote
     * Possible failure paths (handled by {@code GlobalExceptionHandler}):
     * <ul>
     *   <li>{@code 400 VALIDATION_ERROR} — Bean Validation rejected the body</li>
     *   <li>{@code 409 DUPLICATE_TRANSACTION} — the {@code transaction_id} was already used (Redis idempotency)</li>
     *   <li>{@code 500 INTERNAL_ERROR} — anything else; the request id in the response correlates the server log</li>
     * </ul>
     */
    @PostMapping
    @Operation(summary = "Initiate a payment",
               description = "Accepts a payment request, validates input, and returns INITIATED status.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Payment accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> initiate(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }
}

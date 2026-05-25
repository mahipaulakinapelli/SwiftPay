package com.swiftpay.gateway.service;

import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.exception.DuplicateTransactionException;

/**
 * Gateway-side application service for payment initiation.
 *
 * <p>Exists as an interface so production wiring ({@code PaymentServiceImpl})
 * can be swapped for a test double or a future variant without touching the
 * controller.</p>
 */
public interface PaymentService {

    /**
     * Accept a new payment request: claim idempotency in Redis, persist the
     * request as {@code PENDING}, publish a {@code PaymentInitiatedEvent} to
     * Kafka.
     *
     * @param request validated payment input
     * @return the persisted view as a {@link PaymentResponse}
     * @throws DuplicateTransactionException if the {@code transaction_id} has
     *         already been claimed within the idempotency TTL
     */
    PaymentResponse initiatePayment(PaymentRequest request);
}

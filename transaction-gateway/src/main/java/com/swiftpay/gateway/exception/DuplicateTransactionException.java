package com.swiftpay.gateway.exception;

import com.swiftpay.gateway.exception.BusinessException;

import java.util.UUID;

/**
 * Thrown by {@code PaymentServiceImpl} when the client's {@code transaction_id}
 * has already been seen within the idempotency window (Redis-backed).
 *
 * <p>Mapped to HTTP {@code 409 Conflict} with code {@code DUPLICATE_TRANSACTION}
 * by {@link GlobalExceptionHandler#handleDuplicate}.</p>
 */
public class DuplicateTransactionException extends BusinessException {

    private static final String CODE = "DUPLICATE_TRANSACTION";

    /** @param transactionId the id that was already claimed; included in the response message */
    public DuplicateTransactionException(UUID transactionId) {
        super(CODE, "transaction_id " + transactionId + " has already been processed");
    }
}

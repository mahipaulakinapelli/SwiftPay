package com.swiftpay.ledger.exception;

import org.springframework.dao.TransientDataAccessException;

/**
 * Marker for <em>recoverable</em> failures during ledger processing —
 * temporary DB unavailability, deadlocks, connection blips, etc.
 *
 * <p>The {@code PaymentEventConsumer}'s {@code @RetryableTopic.include}
 * filter lists this exception, so throwing it triggers republish to the next
 * retry topic. <em>Do not</em> use this for business-rule failures
 * (insufficient funds, etc.) — those are non-retryable and handled inline
 * by emitting a {@code PaymentFailedEvent}.</p>
 *
 * <p>Extends Spring's {@link TransientDataAccessException} so that real
 * Hibernate / JDBC transient errors fit naturally if we widen the filter
 * later.</p>
 */
public class TransientLedgerException extends TransientDataAccessException {

    public TransientLedgerException(String message) {
        super(message);
    }

    public TransientLedgerException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.swiftpay.analytics.inbox;

/** See {@code com.swiftpay.ledger.inbox.DuplicateEventException} for full semantics. */
public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String message) {
        super(message);
    }
}
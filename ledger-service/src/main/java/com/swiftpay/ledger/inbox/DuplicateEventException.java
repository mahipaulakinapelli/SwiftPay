package com.swiftpay.ledger.inbox;

/**
 * Thrown by {@code InboxService.claim(...)} when the inbox already has a
 * row for {@code (event_id, consumer_group)} — i.e. this consumer group
 * has already started or finished handling this event.
 *
 * <p>Consumers translate this into a "log + ack" no-op so the offset
 * advances without re-running business logic.</p>
 */
public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String message) {
        super(message);
    }
}
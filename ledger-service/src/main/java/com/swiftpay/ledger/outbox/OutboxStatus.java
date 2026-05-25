package com.swiftpay.ledger.outbox;

/** See the gateway counterpart for the full state-machine diagram. */
public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    DEAD_LETTERED
}
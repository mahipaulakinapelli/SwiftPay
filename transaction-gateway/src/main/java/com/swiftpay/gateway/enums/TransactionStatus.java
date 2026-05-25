package com.swiftpay.gateway.enums;

/**
 * Lifecycle of a payment as observed by {@code transaction-gateway}.
 *
 * <p>This enum is intentionally duplicated per service — see the
 * "Contract ownership" section in the root README. The wire format
 * is the string name; renames are explicit, additions are append-only.
 */
public enum TransactionStatus {
    PENDING,
    INITIATED,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED
}
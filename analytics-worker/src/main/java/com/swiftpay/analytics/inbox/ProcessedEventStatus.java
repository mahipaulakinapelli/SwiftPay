package com.swiftpay.analytics.inbox;

/** Lifecycle of a row in analytics' {@code processed_events} inbox. See the ledger counterpart for full notes. */
public enum ProcessedEventStatus {
    PROCESSING,
    PROCESSED,
    FAILED,
    SKIPPED_DUPLICATE
}
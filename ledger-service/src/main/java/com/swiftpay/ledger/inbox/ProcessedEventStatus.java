package com.swiftpay.ledger.inbox;

/**
 * Lifecycle of a row in {@code processed_events}.
 *
 * <ul>
 *   <li>{@link #PROCESSING} — inserted at the start of the consumer's
 *       business transaction; rolled back automatically if the business
 *       transaction fails (so a retry is allowed).</li>
 *   <li>{@link #PROCESSED} — committed by the consumer's business
 *       transaction after the business work succeeded.</li>
 *   <li>{@link #FAILED} — written by the DLT handler once retries are
 *       exhausted; future redeliveries with the same {@code event_id}
 *       are short-circuited.</li>
 *   <li>{@link #SKIPPED_DUPLICATE} — observability marker for redeliveries
 *       that arrived after a {@code PROCESSED} row already existed
 *       (kept only when the consumer wants to audit duplicate-detection).</li>
 * </ul>
 */
public enum ProcessedEventStatus {
    PROCESSING,
    PROCESSED,
    FAILED,
    SKIPPED_DUPLICATE
}
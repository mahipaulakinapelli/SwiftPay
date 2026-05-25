package com.swiftpay.gateway.outbox;

/**
 * Lifecycle of a row in {@code outbox_events}.
 *
 * <pre>
 *           ┌─────────┐
 *           │ PENDING │ ◀────────────────────┐
 *           └────┬────┘                      │ (still has retry budget)
 *                │ claimed by publisher       │
 *                ▼                            │
 *          ┌────────────┐                     │
 *          │ PUBLISHING │                     │
 *          └─────┬──────┘                     │
 *      ┌─────────┴──────────┐                 │
 *      │ ack from broker     │ send failed     │
 *      ▼                     ▼                 │
 * ┌───────────┐         ┌────────┐ ────────────┘
 * │ PUBLISHED │         │ FAILED │
 * └───────────┘         └───┬────┘
 *                           │ retry budget exhausted
 *                           ▼
 *                  ┌───────────────┐
 *                  │ DEAD_LETTERED │  (terminal, needs operator action)
 *                  └───────────────┘
 * </pre>
 */
public enum OutboxStatus {
    /** Just inserted; awaiting publisher pickup. */
    PENDING,
    /** Claimed by a publisher instance; send in flight. A stuck row in this state is rescued back to PENDING by a scheduled sweeper. */
    PUBLISHING,
    /** Broker acked the send; terminal success. */
    PUBLISHED,
    /** Last send attempt failed; still has retry budget remaining. Will be re-attempted with exponential backoff. */
    FAILED,
    /** Retry budget exhausted; terminal — requires operator action or an offline replay tool. */
    DEAD_LETTERED
}
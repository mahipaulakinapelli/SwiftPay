package com.swiftpay.ledger.inbox;

import java.util.UUID;

/**
 * Adapter type the Kafka listener builds from the inbound record + the
 * deserialized event payload. Keeps {@link InboxService} decoupled from
 * Kafka and from any one specific event class.
 *
 * @param eventId        producer-assigned event id (idempotency key)
 * @param consumerGroup  Kafka consumer group recording the attempt
 * @param topic          source topic (original, not a retry topic, for original-event tracing)
 * @param partition      source partition
 * @param offset         source offset
 * @param eventType      FQN of the event class — useful for ops queries
 * @param aggregateId    business id (UUID-as-string for payments) — useful for ops queries
 * @param checksum       optional SHA-256 of the payload for poison-message detection
 */
public record InboxEnvelope(
        UUID eventId,
        String consumerGroup,
        String topic,
        Integer partition,
        Long offset,
        String eventType,
        String aggregateId,
        String checksum
) {
}
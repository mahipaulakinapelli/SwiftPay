package com.swiftpay.analytics.inbox;

import java.util.UUID;

/** See the ledger counterpart for the type's role. */
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
package com.swiftpay.analytics.kafka;

import com.swiftpay.analytics.inbox.DuplicateEventException;
import com.swiftpay.analytics.inbox.InboxEnvelope;
import com.swiftpay.analytics.service.AnalyticsService;
import com.swiftpay.analytics.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Reads {@code payment-completed} events and projects them via
 * {@link AnalyticsService}.
 *
 * <h2>Idempotency</h2>
 * <p>Dedup happens up front through the consumer inbox
 * ({@code processed_events} keyed by {@code (event_id, consumer_group)}).
 * The previous "PK collision on analytics_transactions" fallback is still
 * present in the service (defense in depth), but redeliveries are now
 * normally short-circuited before any projection work.</p>
 *
 * <h2>Failure modes</h2>
 * <ul>
 *   <li>{@link DuplicateEventException} — caught here; the listener returns
 *       so the offset is committed.</li>
 *   <li>Anything else — propagates out of the method so Spring Kafka
 *       doesn't commit the offset, and Kafka redelivers on the next poll.</li>
 * </ul>
 */
@Component
public class AnalyticsEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventConsumer.class);

    static final String CONSUMER_GROUP = "analytics-worker";

    private final AnalyticsService analyticsService;

    /** Constructor-based dependency injection. */
    public AnalyticsEventConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @KafkaListener(topics = "${swiftpay.topics.payment-completed}", groupId = CONSUMER_GROUP)
    public void onPaymentCompleted(
            PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        InboxEnvelope env = new InboxEnvelope(
                event.eventId(),
                CONSUMER_GROUP,
                topic,
                partition != null ? partition : 0,
                offset,
                PaymentCompletedEvent.class.getName(),
                event.transactionId() != null ? event.transactionId().toString() : "unknown",
                null
        );
        if (log.isDebugEnabled()) {
            log.debug("Consumed eventId={} version={} producer={} correlationId={} txId={} topic={}-{}@{}",
                    env.eventId(), event.eventVersion(), event.producer(),
                    event.correlationId(), event.transactionId(),
                    topic, partition, offset);
        }
        if (event.eventVersion() != null && event.eventVersion() > PaymentCompletedEvent.CURRENT_VERSION) {
            log.warn("Forward-compatible parse: received eventVersion={} but consumer understands {} — proceeding with latest known semantics",
                    event.eventVersion(), PaymentCompletedEvent.CURRENT_VERSION);
        }
        try {
            analyticsService.project(env, event);
        } catch (DuplicateEventException dup) {
            log.info("Skipping duplicate eventId={} txId={} — already projected by group={}",
                    env.eventId(), event.transactionId(), CONSUMER_GROUP);
        }
    }
}
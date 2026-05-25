package com.swiftpay.ledger.kafka;

import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.exception.TransientLedgerException;
import com.swiftpay.ledger.inbox.DuplicateEventException;
import com.swiftpay.ledger.inbox.InboxEnvelope;
import com.swiftpay.ledger.inbox.InboxService;
import com.swiftpay.ledger.service.LedgerProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point for the ledger: consumes {@code payment-initiated},
 * dedupes via the inbox, delegates to {@link LedgerProcessingService}, and
 * orchestrates non-blocking retries via Spring Kafka's
 * {@link RetryableTopic} mechanism.
 *
 * <h2>Retry topology (one consumer per delay-tier)</h2>
 * <pre>
 *   payment-initiated  ──fail──▶  payment-initiated-retry-1000  (1 s back-off)
 *                                          │
 *                                          └──fail──▶  payment-initiated-retry-2000  (2 s back-off)
 *                                                              │
 *                                                              └──fail──▶  payment-initiated-dlt  (terminal)
 * </pre>
 *
 * <h2>Per-message flow</h2>
 * <ol>
 *   <li>Resolve the {@link InboxEnvelope} from the inbound record.</li>
 *   <li>Call {@link LedgerProcessingService#process(InboxEnvelope, PaymentInitiatedEvent)}:
 *       the service opens a transaction, inserts a {@code PROCESSING} inbox row
 *       (DB-side {@code UNIQUE(event_id, consumer_group)} blocks duplicate handling),
 *       runs the business logic, marks {@code PROCESSED}, and commits.</li>
 *   <li>{@link DuplicateEventException} = "we already saw this eventId" — return
 *       normally so the offset advances without re-running.</li>
 *   <li>{@link TransientLedgerException} = "DB blip, please retry" — re-thrown
 *       so {@code @RetryableTopic} forwards to the next retry tier.</li>
 * </ol>
 *
 * <h2>DLT handling</h2>
 * <p>{@link #onDlt} writes an inbox {@code FAILED} row so that, if the same
 * event is ever re-injected (operator replay, manual produce), it is
 * recognized as terminally failed and short-circuited.</p>
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    static final String CONSUMER_GROUP = "ledger-service";
    static final String AGGREGATE_TYPE = "Payment";

    private final LedgerProcessingService ledgerProcessingService;
    private final InboxService inboxService;

    /** Constructor-based dependency injection. */
    public PaymentEventConsumer(LedgerProcessingService ledgerProcessingService,
                                InboxService inboxService) {
        this.ledgerProcessingService = ledgerProcessingService;
        this.inboxService = inboxService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "true",
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt",
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            include = { TransientLedgerException.class }
    )
    @KafkaListener(topics = "${swiftpay.topics.payment-initiated}", groupId = CONSUMER_GROUP)
    public void onPaymentInitiated(
            PaymentInitiatedEvent event,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET) long offset
    ) {
        InboxEnvelope env = envelopeFor(event, topic, partition, offset);
        if (log.isDebugEnabled()) {
            log.debug("Consumed eventId={} version={} producer={} correlationId={} txId={} topic={}-{}@{}",
                    env.eventId(), event.eventVersion(), event.producer(),
                    event.correlationId(), event.transactionId(),
                    topic, partition, offset);
        }
        if (event.eventVersion() != null && event.eventVersion() > PaymentInitiatedEvent.CURRENT_VERSION) {
            log.warn("Forward-compatible parse: received eventVersion={} but consumer understands {} — proceeding with latest known semantics",
                    event.eventVersion(), PaymentInitiatedEvent.CURRENT_VERSION);
        }
        try {
            ledgerProcessingService.process(env, event);
        } catch (DuplicateEventException dup) {
            log.info("Skipping duplicate eventId={} txId={} — already processed by group={}",
                    env.eventId(), event.transactionId(), CONSUMER_GROUP);
            // returning normally → RetryableTopic considers the message handled,
            // the consumer commits the offset, no further redelivery.
        }
    }

    /**
     * Terminal handler — runs once the retry chain is exhausted.
     * Records a {@link com.swiftpay.ledger.inbox.ProcessedEventStatus#FAILED}
     * inbox row so the event won't be re-processed if re-injected.
     */
    @DltHandler
    public void onDlt(
            PaymentInitiatedEvent event,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET) long offset
    ) {
        InboxEnvelope env = envelopeFor(event, topic, partition, offset);
        log.error("DLT: eventId={} txId={} correlationId={} landed in {}@{}-{} after retries",
                env.eventId(), event.transactionId(), event.correlationId(), topic, partition, offset);
        try {
            inboxService.markFailed(env, "Retry budget exhausted; landed in DLT topic " + topic);
        } catch (RuntimeException ex) {
            log.error("Failed to persist DLT inbox record for eventId={} — manual intervention required",
                    env.eventId(), ex);
        }
    }

    private static InboxEnvelope envelopeFor(PaymentInitiatedEvent event,
                                             String topic,
                                             Integer partition,
                                             long offset) {
        return new InboxEnvelope(
                event.eventId(),
                CONSUMER_GROUP,
                topic,
                partition != null ? partition : -1,
                offset,
                PaymentInitiatedEvent.class.getName(),
                event.transactionId() != null ? event.transactionId().toString() : "unknown",
                null
        );
    }
}
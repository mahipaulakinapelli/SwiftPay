package com.swiftpay.ledger.kafka;

import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.event.PaymentFailedEvent;
import com.swiftpay.ledger.config.TopicsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the ledger's terminal-state events:
 * {@code payment-completed} on success and {@code payment-failed} on a
 * business-rule failure.
 *
 * <p>All sends are keyed by the {@code transactionId} so downstream consumers
 * see in-partition ordering for a given payment (the original
 * {@code payment-initiated} event was also keyed this way).</p>
 */
@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TopicsProperties topics;

    /** Constructor-based dependency injection. */
    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate, TopicsProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    /** Publish a {@code PaymentCompletedEvent} on success. */
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        String key = event.transactionId().toString();
        kafkaTemplate.send(topics.paymentCompleted(), key, event).whenComplete((r, ex) -> {
            if (ex != null) log.error("Failed to publish PaymentCompletedEvent {}", key, ex);
            else log.info("Published PaymentCompletedEvent {} -> {}-{}@{}", key,
                    r.getRecordMetadata().topic(), r.getRecordMetadata().partition(), r.getRecordMetadata().offset());
        });
    }

    /** Publish a {@code PaymentFailedEvent} on a business-rule failure. */
    public void publishPaymentFailed(PaymentFailedEvent event) {
        String key = event.transactionId().toString();
        kafkaTemplate.send(topics.paymentFailed(), key, event).whenComplete((r, ex) -> {
            if (ex != null) log.error("Failed to publish PaymentFailedEvent {}", key, ex);
            else log.info("Published PaymentFailedEvent {} -> {}-{}@{}", key,
                    r.getRecordMetadata().topic(), r.getRecordMetadata().partition(), r.getRecordMetadata().offset());
        });
    }
}
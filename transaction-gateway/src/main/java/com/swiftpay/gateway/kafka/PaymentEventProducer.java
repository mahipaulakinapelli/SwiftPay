package com.swiftpay.gateway.kafka;

import com.swiftpay.gateway.event.PaymentInitiatedEvent;
import com.swiftpay.gateway.config.TopicsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes {@link PaymentInitiatedEvent} to the {@code payment-initiated}
 * Kafka topic.
 *
 * <p>The message key is the {@code transactionId} (as a string). This is what
 * pins all events for a given payment to the same partition — combined with
 * Kafka's per-partition ordering it gives us "in-order delivery per
 * transaction" without locking the whole topic.</p>
 *
 * <p>The send is asynchronous; success/failure is logged via the
 * {@link CompletableFuture} callback. Returning the future lets callers
 * compose further behavior if they want to block.</p>
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

    /**
     * Send a {@code PaymentInitiatedEvent} keyed by transaction id.
     *
     * @param event the event payload
     * @return the future resolving to the broker {@code SendResult} on success,
     *         or completing exceptionally if the broker rejects the message
     */
    public CompletableFuture<SendResult<String, Object>> publishPaymentInitiated(PaymentInitiatedEvent event) {
        String key = event.transactionId().toString();
        String topic = topics.paymentInitiated();

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish PaymentInitiatedEvent {} to topic {}", key, topic, ex);
            } else {
                var meta = result.getRecordMetadata();
                log.info("Published PaymentInitiatedEvent {} → {}-{}@{}",
                        key, meta.topic(), meta.partition(), meta.offset());
            }
        });
        return future;
    }
}
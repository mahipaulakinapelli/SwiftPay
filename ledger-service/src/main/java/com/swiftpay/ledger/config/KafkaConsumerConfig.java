package com.swiftpay.ledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer wiring for the ledger.
 *
 * <h2>Deserialization edge cases</h2>
 * <p>The value deserializer is wrapped in {@link ErrorHandlingDeserializer},
 * so a malformed payload (truncated JSON, missing {@code __TypeId__} header,
 * unknown event class) does NOT crash and stall the consumer thread on the
 * bad offset. Instead the listener receives {@code null} for the value and
 * the {@link DefaultErrorHandler} logs + skips the record, advancing the
 * offset and unblocking the partition. The DLT route inside
 * {@code @RetryableTopic} continues to work for application-layer failures.</p>
 *
 * <p>{@code AckMode.RECORD} is chosen so {@code @RetryableTopic} controls
 * offset commits — manual ack mode fights the retry framework.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties,
                                                           ObjectMapper objectMapper) {
        Map<String, Object> configs = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        // Single-schema-per-topic: the `payment-initiated` topic only ever carries
        // PaymentInitiatedEvent, so the cleanest contract is to bind the deserializer
        // to that local class directly and ignore any `__TypeId__` header the producer
        // might write. This decouples us from the producer's Java FQN entirely —
        // exactly what the poly-repo cutover needs (no shared class identity on the wire).
        //
        // 3-arg ctor: targetType, ObjectMapper, useHeadersIfPresent=false.
        @SuppressWarnings({"rawtypes", "unchecked"})
        JsonDeserializer<Object> innerValue = (JsonDeserializer)
                new JsonDeserializer<>(PaymentInitiatedEvent.class, objectMapper, false);

        // Wrap the value deserializer in ErrorHandlingDeserializer so a bad
        // payload becomes a DeserializationException carried in record headers
        // rather than an unrecoverable exception on the polling thread.
        ErrorHandlingDeserializer<Object> safeValue = new ErrorHandlingDeserializer<>(innerValue);

        return new DefaultKafkaConsumerFactory<>(
                configs,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                safeValue
        );
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // Default behavior: log and skip the record (advance offset) when the
        // deserializer signals a failure. RetryableTopic still handles
        // application-layer retries for business code.
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) ->
                log.error("Skipping poison record at topic={} partition={} offset={} cause={}",
                        record.topic(), record.partition(), record.offset(), exception.toString())
        );
        // Don't retry deserialization failures — they're never transient.
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class
        );
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
package com.swiftpay.analytics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.analytics.event.PaymentCompletedEvent;
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
 * Kafka consumer wiring for the analytics worker.
 *
 * <p>Same pattern as the ledger consumer config:</p>
 * <ul>
 *   <li>{@link JsonDeserializer} carries the application's primary
 *       {@link ObjectMapper} so snake_case + JSR310 stay in sync with producers.</li>
 *   <li>{@link ErrorHandlingDeserializer} wraps both key and value so that
 *       a malformed record becomes a logged-and-skipped event rather than a
 *       stalled partition.</li>
 *   <li>{@link DefaultErrorHandler} marks
 *       {@link org.springframework.kafka.support.serializer.DeserializationException}
 *       as non-retryable so the listener container advances past the bad offset.</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties,
                                                           ObjectMapper objectMapper) {
        Map<String, Object> configs = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        // Single-schema-per-topic: bind directly to PaymentCompletedEvent and ignore
        // any __TypeId__ header. See ledger-service KafkaConsumerConfig for the
        // poly-repo rationale.
        @SuppressWarnings({"rawtypes", "unchecked"})
        JsonDeserializer<Object> innerValue = (JsonDeserializer)
                new JsonDeserializer<>(PaymentCompletedEvent.class, objectMapper, false);

        ErrorHandlingDeserializer<Object> safeValue = new ErrorHandlingDeserializer<>(innerValue);
        return new DefaultKafkaConsumerFactory<>(
                configs,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                safeValue
        );
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) ->
                log.error("Skipping poison record at topic={} partition={} offset={} cause={}",
                        record.topic(), record.partition(), record.offset(), exception.toString())
        );
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
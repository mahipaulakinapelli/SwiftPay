package com.swiftpay.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.gateway.event.PaymentInitiatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer wiring for the gateway.
 *
 * <p>The default Spring-Boot-wired {@code JsonSerializer} uses a fresh
 * {@code new ObjectMapper()} without JSR310 support, which silently fails
 * on {@link java.time.OffsetDateTime}. This config explicitly hands the
 * application's primary {@link ObjectMapper} (snake_case + JSR310) to the
 * serializer, so events serialize correctly to the wire format the
 * consumers expect.</p>
 *
 * <p>{@code setAddTypeInfo(true)} preserves the {@code __TypeId__} header,
 * which lets {@code JsonDeserializer} on the consumer side reconstruct the
 * correct record subtype.</p>
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * Builds the {@link ProducerFactory} with our customised {@link JsonSerializer}.
     * Other properties (acks, idempotence, bootstrap-servers) flow in from
     * {@code spring.kafka.producer.*} in application.yml.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties,
                                                           ObjectMapper objectMapper) {
        Map<String, Object> configs = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.setAddTypeInfo(true);

        // Wire format uses the logical name 'payment-initiated' (not the Java FQN),
        // so consumers in other repos don't depend on our package layout.
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setIdClassMapping(Map.of(
                "payment-initiated", PaymentInitiatedEvent.class
        ));
        valueSerializer.setTypeMapper(typeMapper);

        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(configs, new StringSerializer(), valueSerializer);
        factory.setProducerPerThread(false);
        return factory;
    }

    /** Single template reused across the application. */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
package com.swiftpay.gateway.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.gateway.entity.OutboxEvent;
import com.swiftpay.gateway.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Helper that business services call <em>inside</em> their own
 * {@code @Transactional} boundary to record an event for downstream publish.
 *
 * <p>Because the insert runs in the caller's transaction, the event row and
 * the aggregate's row commit (or roll back) atomically — no dual-write
 * window. The actual Kafka send is then the relay's problem
 * ({@link OutboxPublisher}).</p>
 */
@Service
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /** Constructor-based dependency injection. */
    public OutboxEventService(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persist a new {@link OutboxStatus#PENDING} event row.
     *
     * @param aggregateType type tag for ops queries (e.g. {@code "Payment"})
     * @param aggregateId   business id (used as Kafka message key on publish)
     * @param topic         target Kafka topic
     * @param payload       event payload — serialized via the application's primary {@link ObjectMapper}
     */
    public void record(String aggregateType, String aggregateId, String topic, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(payload.getClass().getName());
        event.setTopic(topic);
        event.setPayload(serialize(payload));
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(OffsetDateTime.now());
        outboxRepository.save(event);
        log.info("Outbox recorded id={} type={} aggregate={}:{} topic={}",
                event.getId(), event.getEventType(), aggregateType, aggregateId, topic);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload of type " + payload.getClass(), e);
        }
    }
}
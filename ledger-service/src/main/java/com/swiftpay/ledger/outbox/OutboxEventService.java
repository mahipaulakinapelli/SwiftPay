package com.swiftpay.ledger.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledger.entity.OutboxEvent;
import com.swiftpay.ledger.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Helper that the ledger settlement service calls <em>inside</em> its
 * {@code @Transactional} boundary to record a downstream event.
 *
 * <p>Because the insert runs in the caller's transaction, the ledger row,
 * the account balances, and the event row commit (or roll back) atomically
 * — no path leaves a half-settled ledger with a missing event.</p>
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
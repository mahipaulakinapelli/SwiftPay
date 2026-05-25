package com.swiftpay.analytics.inbox;

import com.swiftpay.analytics.entity.ProcessedEvent;
import com.swiftpay.analytics.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Analytics inbox — see the ledger counterpart for full notes; behavior is identical. */
@Service
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    private final ProcessedEventRepository repository;

    /** Constructor-based dependency injection. */
    public InboxService(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    public void claim(InboxEnvelope env) {
        Optional<ProcessedEvent> existing =
                repository.findByEventIdAndConsumerGroup(env.eventId(), env.consumerGroup());
        if (existing.isPresent()) {
            log.info("Inbox SKIP eventId={} group={} priorStatus={} — duplicate redelivery",
                    env.eventId(), env.consumerGroup(), existing.get().getStatus());
            throw new DuplicateEventException(
                    "Already seen eventId=" + env.eventId() + " group=" + env.consumerGroup());
        }
        ProcessedEvent row = new ProcessedEvent();
        row.setId(UUID.randomUUID());
        row.setEventId(env.eventId());
        row.setConsumerGroup(env.consumerGroup());
        row.setTopic(env.topic());
        row.setPartition(env.partition());
        row.setOffsetValue(env.offset());
        row.setEventType(env.eventType());
        row.setAggregateId(env.aggregateId());
        row.setChecksum(env.checksum());
        row.setStatus(ProcessedEventStatus.PROCESSING);
        row.setCreatedAt(OffsetDateTime.now());
        try {
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException race) {
            log.info("Inbox SKIP eventId={} group={} — lost insert race to a sibling consumer",
                    env.eventId(), env.consumerGroup());
            throw new DuplicateEventException(
                    "Concurrent claim lost the race for eventId=" + env.eventId());
        }
        log.debug("Inbox CLAIM eventId={} group={} topic={}-{}@{}",
                env.eventId(), env.consumerGroup(),
                env.topic(), env.partition(), env.offset());
    }

    public void markProcessed(InboxEnvelope env) {
        int n = repository.updateStatus(
                env.eventId(), env.consumerGroup(),
                ProcessedEventStatus.PROCESSED, OffsetDateTime.now(), null);
        log.debug("Inbox PROCESSED eventId={} group={} rows={}", env.eventId(), env.consumerGroup(), n);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(InboxEnvelope env, String reason) {
        Optional<ProcessedEvent> existing =
                repository.findByEventIdAndConsumerGroup(env.eventId(), env.consumerGroup());
        if (existing.isPresent()) {
            repository.updateStatus(
                    env.eventId(), env.consumerGroup(),
                    ProcessedEventStatus.FAILED, OffsetDateTime.now(), truncate(reason));
            log.warn("Inbox FAILED (update) eventId={} group={} reason={}",
                    env.eventId(), env.consumerGroup(), reason);
            return;
        }
        ProcessedEvent row = new ProcessedEvent();
        row.setId(UUID.randomUUID());
        row.setEventId(env.eventId());
        row.setConsumerGroup(env.consumerGroup());
        row.setTopic(env.topic());
        row.setPartition(env.partition());
        row.setOffsetValue(env.offset());
        row.setEventType(env.eventType());
        row.setAggregateId(env.aggregateId());
        row.setChecksum(env.checksum());
        row.setStatus(ProcessedEventStatus.FAILED);
        row.setLastError(truncate(reason));
        row.setProcessedAt(OffsetDateTime.now());
        row.setCreatedAt(OffsetDateTime.now());
        try {
            repository.saveAndFlush(row);
            log.warn("Inbox FAILED (insert) eventId={} group={} reason={}",
                    env.eventId(), env.consumerGroup(), reason);
        } catch (DataIntegrityViolationException race) {
            repository.updateStatus(
                    env.eventId(), env.consumerGroup(),
                    ProcessedEventStatus.FAILED, OffsetDateTime.now(), truncate(reason));
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }
}
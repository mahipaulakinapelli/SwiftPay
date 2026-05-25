package com.swiftpay.ledger.inbox;

import com.swiftpay.ledger.entity.ProcessedEvent;
import com.swiftpay.ledger.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumer-side dedup primitive.
 *
 * <p>The intended call pattern from a {@link org.springframework.kafka.annotation.KafkaListener}:
 * <pre>{@code
 * try {
 *     inboxService.claim(envelope);     // inserts PROCESSING row, throws if duplicate
 *     business.process(payload);        // mutates aggregate, inserts outbox row
 *     inboxService.markProcessed(envelope);
 * } catch (DuplicateEventException e) {
 *     // already handled — return to ack the offset
 * }
 * }</pre>
 *
 * <p>The {@code claim} insert is made inside the caller's outer
 * transaction, so a business-logic failure rolls the inbox row back with
 * it and the next redelivery is allowed to retry.</p>
 */
@Service
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    private final ProcessedEventRepository repository;

    /** Constructor-based dependency injection. */
    public InboxService(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Insert a {@code PROCESSING} row for this event in this consumer group.
     *
     * @throws DuplicateEventException if a row for {@code (event_id, group)} already exists
     */
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
            // Two consumer pods raced past the existence check at the same time;
            // the DB-side UNIQUE constraint resolved the race deterministically.
            log.info("Inbox SKIP eventId={} group={} — lost insert race to a sibling consumer",
                    env.eventId(), env.consumerGroup());
            throw new DuplicateEventException(
                    "Concurrent claim lost the race for eventId=" + env.eventId());
        }
        log.debug("Inbox CLAIM eventId={} group={} topic={}-{}@{}",
                env.eventId(), env.consumerGroup(),
                env.topic(), env.partition(), env.offset());
    }

    /** Flip the claimed row to {@link ProcessedEventStatus#PROCESSED}. */
    public void markProcessed(InboxEnvelope env) {
        int n = repository.updateStatus(
                env.eventId(), env.consumerGroup(),
                ProcessedEventStatus.PROCESSED, OffsetDateTime.now(), null);
        log.debug("Inbox PROCESSED eventId={} group={} rows={}", env.eventId(), env.consumerGroup(), n);
    }

    /**
     * Terminal failure — invoked from the DLT handler after the retry budget
     * is exhausted. Marks (or inserts) the row as {@link ProcessedEventStatus#FAILED}
     * so a future re-injection of the same event is short-circuited.
     */
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
            // Another thread inserted between findBy and save — fall back to update.
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
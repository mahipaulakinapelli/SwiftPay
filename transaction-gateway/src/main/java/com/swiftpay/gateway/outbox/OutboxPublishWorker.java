package com.swiftpay.gateway.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.gateway.entity.OutboxEvent;
import com.swiftpay.gateway.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The transactional half of the outbox relay — kept in its own bean so
 * the {@link OutboxPublisher} scheduler can invoke each {@code REQUIRES_NEW}
 * method through the Spring AOP proxy. Self-invocation from within the
 * publisher bean would silently bypass the proxy and run with no
 * transaction at all, which would defeat the lock-and-claim step.
 */
@Component
public class OutboxPublishWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishWorker.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Constructor-based dependency injection. */
    public OutboxPublishWorker(OutboxEventRepository outboxRepository,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Phase 1 — claim a batch of PENDING/FAILED rows whose backoff window has
     * elapsed and flip them to {@link OutboxStatus#PUBLISHING}. Commits so the
     * "in-flight" state is observable to ops queries and to the stuck-row sweeper.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> claimBatch(int batchSize, int maxBackoffSeconds) {
        List<OutboxEvent> rows = outboxRepository.claimPendingForPublish(batchSize, maxBackoffSeconds);
        OffsetDateTime now = OffsetDateTime.now();
        for (OutboxEvent row : rows) {
            row.setStatus(OutboxStatus.PUBLISHING);
            row.setLastAttemptAt(now);
        }
        return rows.stream().map(OutboxEvent::getId).toList();
    }

    /**
     * Phase 2 — load the row, send to Kafka with a bounded timeout, and persist
     * the terminal status ({@link OutboxStatus#PUBLISHED}, {@link OutboxStatus#FAILED},
     * or {@link OutboxStatus#DEAD_LETTERED}) in this row's own transaction so a
     * single bad row never blocks the rest of the batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeOne(UUID id, long sendTimeoutMs, int maxRetryCount) {
        OutboxEvent row = outboxRepository.findOneById(id);
        if (row == null || row.getStatus() != OutboxStatus.PUBLISHING) {
            // Rescued or finalized by another path — nothing to do.
            return;
        }

        Object payload;
        try {
            payload = objectMapper.readValue(row.getPayload(), resolveEventClass(row.getEventType()));
        } catch (ClassNotFoundException | java.io.IOException poison) {
            row.setStatus(OutboxStatus.DEAD_LETTERED);
            row.setLastError(truncate("poison-payload: " + poison));
            row.setProcessedAt(OffsetDateTime.now());
            log.error("Outbox DEAD_LETTERED (poison) id={} type={} cause={}",
                    row.getId(), row.getEventType(), poison.toString(), poison);
            return;
        }

        try {
            kafkaTemplate.send(row.getTopic(), row.getAggregateId(), payload)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);

            row.setStatus(OutboxStatus.PUBLISHED);
            row.setProcessedAt(OffsetDateTime.now());
            row.setLastError(null);
            log.info("Outbox PUBLISHED id={} topic={} aggregate={}:{} retries={}",
                    row.getId(), row.getTopic(),
                    row.getAggregateType(), row.getAggregateId(), row.getRetryCount());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            recordSendFailure(row, ie, maxRetryCount);
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            recordSendFailure(row, ex, maxRetryCount);
        }
    }

    /** Sweeper task — see {@link OutboxPublisher#rescueStuckPublishing()}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int rescueStuckPublishing(int thresholdSeconds) {
        return outboxRepository.rescueStuckPublishing(thresholdSeconds);
    }

    private void recordSendFailure(OutboxEvent row, Throwable cause, int maxRetryCount) {
        int next = row.getRetryCount() + 1;
        row.setRetryCount(next);
        row.setLastError(truncate(cause.toString()));
        if (next >= maxRetryCount) {
            row.setStatus(OutboxStatus.DEAD_LETTERED);
            row.setProcessedAt(OffsetDateTime.now());
            log.error("Outbox DEAD_LETTERED id={} topic={} retries={} cause={}",
                    row.getId(), row.getTopic(), next, cause.toString(), cause);
        } else {
            row.setStatus(OutboxStatus.FAILED);
            log.warn("Outbox FAILED id={} topic={} retries={} cause={} — will retry after backoff",
                    row.getId(), row.getTopic(), next, cause.toString());
        }
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    /**
     * Maps the stored {@code event_type} FQN to a class that exists in this service.
     * Aliases the legacy {@code com.swiftpay.common.event.*} FQNs (written before the
     * poly-repo refactor) to their local {@code com.swiftpay.gateway.event.*} replicas
     * so existing outbox rows don't poison-pill the relay.
     */
    private static Class<?> resolveEventClass(String eventType) throws ClassNotFoundException {
        if (eventType != null && eventType.startsWith("com.swiftpay.common.event.")) {
            String local = "com.swiftpay.gateway.event." + eventType.substring("com.swiftpay.common.event.".length());
            try {
                return Class.forName(local);
            } catch (ClassNotFoundException ignored) {
                // fall through to original FQN attempt below
            }
        }
        return Class.forName(eventType);
    }
}
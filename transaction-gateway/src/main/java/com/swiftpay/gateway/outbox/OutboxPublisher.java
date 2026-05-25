package com.swiftpay.gateway.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Scheduler-only façade for the outbox relay. All DB and Kafka work lives
 * in {@link OutboxPublishWorker}; this class exists so the {@code @Scheduled}
 * methods can call worker methods through Spring's AOP proxy (in-class
 * self-invocation would skip the {@code @Transactional} interceptor).
 *
 * <h2>State machine ({@link OutboxStatus})</h2>
 * <pre>
 *   PENDING ──claim──▶ PUBLISHING ──ack──▶ PUBLISHED   (terminal)
 *                         │
 *                         ├──send-failure (retry budget left)──▶ FAILED ──backoff──▶ PUBLISHING
 *                         ├──send-failure (budget exhausted)──▶ DEAD_LETTERED (terminal)
 *                         ├──poison payload                ──▶ DEAD_LETTERED (terminal)
 *                         └──pod crash mid-flight          ──▶ (rescue sweeper) ──▶ PENDING
 * </pre>
 *
 * <h2>Tunables (application.yml)</h2>
 * <ul>
 *   <li>{@code swiftpay.outbox.poll-delay-ms} (default 500)</li>
 *   <li>{@code swiftpay.outbox.batch-size} (default 50)</li>
 *   <li>{@code swiftpay.outbox.send-timeout-ms} (default 5000)</li>
 *   <li>{@code swiftpay.outbox.max-retry-count} (default 8)</li>
 *   <li>{@code swiftpay.outbox.max-backoff-seconds} (default 300)</li>
 *   <li>{@code swiftpay.outbox.rescue-delay-ms} (default 30000)</li>
 *   <li>{@code swiftpay.outbox.stuck-publishing-threshold-seconds} (default 60)</li>
 * </ul>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxPublishWorker worker;

    /** Constructor-based dependency injection. */
    public OutboxPublisher(OutboxPublishWorker worker) {
        this.worker = worker;
    }

    @Value("${swiftpay.outbox.batch-size:50}")
    private int batchSize;

    @Value("${swiftpay.outbox.send-timeout-ms:5000}")
    private long sendTimeoutMs;

    @Value("${swiftpay.outbox.max-retry-count:8}")
    private int maxRetryCount;

    @Value("${swiftpay.outbox.max-backoff-seconds:300}")
    private int maxBackoffSeconds;

    @Value("${swiftpay.outbox.stuck-publishing-threshold-seconds:60}")
    private int stuckPublishingThresholdSeconds;

    @Scheduled(fixedDelayString = "${swiftpay.outbox.poll-delay-ms:500}")
    public void publishBatch() {
        List<UUID> claimed = worker.claimBatch(batchSize, maxBackoffSeconds);
        if (claimed.isEmpty()) {
            return;
        }
        log.debug("Outbox claimed batch size={}", claimed.size());
        for (UUID id : claimed) {
            worker.finalizeOne(id, sendTimeoutMs, maxRetryCount);
        }
    }

    @Scheduled(fixedDelayString = "${swiftpay.outbox.rescue-delay-ms:30000}")
    public void rescueStuckPublishing() {
        int rescued = worker.rescueStuckPublishing(stuckPublishingThresholdSeconds);
        if (rescued > 0) {
            log.warn("Outbox RESCUE: reset {} stuck PUBLISHING row(s) back to PENDING (threshold={}s)",
                    rescued, stuckPublishingThresholdSeconds);
        }
    }
}
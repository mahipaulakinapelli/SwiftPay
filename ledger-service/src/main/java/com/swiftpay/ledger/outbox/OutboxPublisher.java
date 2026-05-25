package com.swiftpay.ledger.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Scheduler-only façade for the ledger's outbox relay. See gateway counterpart for full notes. */
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
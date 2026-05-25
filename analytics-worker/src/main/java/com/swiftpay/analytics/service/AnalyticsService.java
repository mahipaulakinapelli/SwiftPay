package com.swiftpay.analytics.service;

import com.swiftpay.analytics.entity.AnalyticsTransaction;
import com.swiftpay.analytics.inbox.InboxEnvelope;
import com.swiftpay.analytics.inbox.InboxService;
import com.swiftpay.analytics.repository.AnalyticsTransactionRepository;
import com.swiftpay.analytics.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects {@link PaymentCompletedEvent} records into
 * {@code analytics_transactions}.
 *
 * <h2>Two-layer dedup</h2>
 * <ol>
 *   <li><strong>Inbox</strong> ({@link InboxService}) — primary, runs first;
 *       blocks Kafka redeliveries on {@code (event_id, consumer_group)}.</li>
 *   <li><strong>PK collision</strong> on {@code analytics_transactions.transaction_id} —
 *       defense in depth for re-projection across deployments / consumer-group renames.</li>
 * </ol>
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsTransactionRepository repository;
    private final InboxService inboxService;

    /** Constructor-based dependency injection. */
    public AnalyticsService(AnalyticsTransactionRepository repository, InboxService inboxService) {
        this.repository = repository;
        this.inboxService = inboxService;
    }

    /**
     * Inbox-aware projection entry point used by the Kafka listener.
     *
     * @throws com.swiftpay.analytics.inbox.DuplicateEventException for a previously-seen {@code event_id}
     */
    @Transactional
    public void project(InboxEnvelope env, PaymentCompletedEvent event) {
        inboxService.claim(env);

        if (repository.existsByTransactionId(event.transactionId())) {
            // Inbox said it's new, but the projection table already has the row.
            // Means the inbox was wiped (operator action) or this is a new consumer
            // group consuming the same topic — either way, log and skip the insert.
            log.info("Skip projection: txId={} already present (eventId={}, inbox row created)",
                    event.transactionId(), env.eventId());
            inboxService.markProcessed(env);
            return;
        }

        AnalyticsTransaction entity = new AnalyticsTransaction();
        entity.setTransactionId(event.transactionId());
        entity.setSenderId(event.senderId());
        entity.setReceiverId(event.receiverId());
        entity.setAmount(event.amount());
        entity.setCurrency(event.currency());
        entity.setSenderBalanceAfter(event.senderBalanceAfter());
        entity.setReceiverBalanceAfter(event.receiverBalanceAfter());
        entity.setEventId(event.eventId());
        entity.setOccurredAt(event.occurredAt());

        repository.save(entity);

        inboxService.markProcessed(env);
        log.info("Projected eventId={} txId={} amount={} {} correlationId={}",
                env.eventId(), event.transactionId(), event.amount(), event.currency(), event.correlationId());
    }

    /**
     * Aggregate the projection table into the volume summary returned by
     * {@code GET /analytics/volume}.
     */
    @Transactional(readOnly = true)
    public AnalyticsTransactionRepository.VolumeSummary getVolume() {
        return repository.getVolumeSummary();
    }
}
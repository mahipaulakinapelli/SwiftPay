package com.swiftpay.ledger.service;

import com.swiftpay.ledger.enums.AccountStatus;
import com.swiftpay.ledger.enums.TransactionStatus;
import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.event.PaymentFailedEvent;
import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.cache.BalancesChangedEvent;
import com.swiftpay.ledger.config.TopicsProperties;
import com.swiftpay.ledger.entity.Account;
import com.swiftpay.ledger.entity.Transaction;
import com.swiftpay.ledger.exception.TransientLedgerException;
import com.swiftpay.ledger.inbox.InboxEnvelope;
import com.swiftpay.ledger.inbox.InboxService;
import com.swiftpay.ledger.outbox.OutboxEventService;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Core financial-settlement service.
 *
 * <h2>Transaction boundary</h2>
 * <p>One {@code @Transactional} encloses:</p>
 * <ol>
 *   <li>Inbox claim ({@link InboxService#claim}) — throws
 *       {@link com.swiftpay.ledger.inbox.DuplicateEventException} for redeliveries.</li>
 *   <li>Debit + credit with optimistic locking on accounts.</li>
 *   <li>Persist ledger {@code transactions} row.</li>
 *   <li>Record outgoing {@code payment-completed} or {@code payment-failed} in the outbox.</li>
 *   <li>Mark inbox row {@code PROCESSED}.</li>
 *   <li>Publish {@link BalancesChangedEvent} for AFTER_COMMIT cache eviction.</li>
 * </ol>
 *
 * <p>All in one DB commit. A rollback discards every side effect.</p>
 *
 * <h2>Failure modes</h2>
 * <ul>
 *   <li><strong>Duplicate event</strong> — propagates {@link com.swiftpay.ledger.inbox.DuplicateEventException};
 *       caller (listener) catches it and acks the offset.</li>
 *   <li><strong>Business failure</strong> (missing sender, frozen account,
 *       insufficient funds) — persists {@code FAILED} transaction + outbox
 *       {@code payment-failed} row; inbox is marked {@code PROCESSED} (we
 *       did decide on this event, even if the outcome was a business no-op).</li>
 *   <li><strong>Transient failure</strong> — throws
 *       {@link TransientLedgerException}, transaction rolls back, retry topology
 *       re-delivers.</li>
 * </ul>
 */
@Service
public class LedgerProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LedgerProcessingService.class);

    static final String AGGREGATE_TYPE = "Payment";

    private final TransactionRepository txRepo;
    private final AccountRepository accountRepo;
    private final FailureInjector failureInjector;
    private final OutboxEventService outboxEventService;
    private final InboxService inboxService;
    private final ApplicationEventPublisher domainEventPublisher;
    private final TopicsProperties topics;

    /** Constructor-based dependency injection. */
    public LedgerProcessingService(TransactionRepository txRepo,
                                   AccountRepository accountRepo,
                                   FailureInjector failureInjector,
                                   OutboxEventService outboxEventService,
                                   InboxService inboxService,
                                   ApplicationEventPublisher domainEventPublisher,
                                   TopicsProperties topics) {
        this.txRepo = txRepo;
        this.accountRepo = accountRepo;
        this.failureInjector = failureInjector;
        this.outboxEventService = outboxEventService;
        this.inboxService = inboxService;
        this.domainEventPublisher = domainEventPublisher;
        this.topics = topics;
    }

    @Transactional
    public void process(InboxEnvelope env, PaymentInitiatedEvent event) {
        // Step 1: dedupe. Throws DuplicateEventException for redeliveries
        // (caller treats that as "ack and move on"). Must run first so we
        // don't reject business work after the dedup check has passed.
        inboxService.claim(env);

        // Test-only injection point — simulates DB blips.
        if (failureInjector.consumeIfArmed(event.transactionId())) {
            throw new TransientLedgerException(
                    "Simulated transient DB failure for transactionId=" + event.transactionId());
        }

        Transaction tx = newTransaction(event);
        tx = txRepo.save(tx);
        log.info("Processing eventId={} txId={} sender={} receiver={} amount={} {}",
                event.eventId(), tx.getTransactionId(),
                tx.getSenderId(), tx.getReceiverId(), tx.getAmount(), tx.getCurrency());

        Optional<Account> senderOpt = accountRepo.findByUserIdAndCurrency(event.senderId(), event.currency());
        if (senderOpt.isEmpty()) {
            fail(tx, event, "SENDER_ACCOUNT_NOT_FOUND",
                    "Sender " + event.senderId() + " has no " + event.currency() + " account");
            inboxService.markProcessed(env);
            return;
        }

        Account sender = senderOpt.get();
        if (sender.getStatus() != AccountStatus.ACTIVE) {
            fail(tx, event, "SENDER_ACCOUNT_INACTIVE", "Sender account status is " + sender.getStatus());
            inboxService.markProcessed(env);
            return;
        }

        if (sender.getBalance().compareTo(event.amount()) < 0) {
            fail(tx, event, "INSUFFICIENT_FUNDS",
                    "Sender balance " + sender.getBalance() + " < amount " + event.amount());
            inboxService.markProcessed(env);
            return;
        }

        sender.setBalance(sender.getBalance().subtract(event.amount()));

        Account receiver = accountRepo.findByUserIdAndCurrency(event.receiverId(), event.currency())
                .orElseGet(() -> openAccount(event.receiverId(), event.currency()));
        receiver.setBalance(receiver.getBalance().add(event.amount()));

        accountRepo.save(sender);
        accountRepo.save(receiver);

        tx.setStatus(TransactionStatus.COMPLETED);

        PaymentCompletedEvent completed = PaymentCompletedEvent.of(
                tx.getTransactionId(), tx.getSenderId(), tx.getReceiverId(),
                tx.getAmount(), tx.getCurrency(),
                sender.getBalance(), receiver.getBalance(),
                event.correlationId()
        );
        outboxEventService.record(
                AGGREGATE_TYPE,
                tx.getTransactionId().toString(),
                topics.paymentCompleted(),
                completed
        );

        inboxService.markProcessed(env);
        domainEventPublisher.publishEvent(new BalancesChangedEvent(event.senderId(), event.receiverId()));

        log.info("Completed txId={} sender_balance={} receiver_balance={}",
                tx.getTransactionId(), sender.getBalance(), receiver.getBalance());
    }

    private void fail(Transaction tx, PaymentInitiatedEvent event, String code, String reason) {
        tx.setStatus(TransactionStatus.FAILED);
        PaymentFailedEvent failedEvent = PaymentFailedEvent.of(
                tx.getTransactionId(), tx.getSenderId(), tx.getReceiverId(),
                tx.getAmount(), tx.getCurrency(),
                code, reason, event.correlationId()
        );
        outboxEventService.record(
                AGGREGATE_TYPE,
                tx.getTransactionId().toString(),
                topics.paymentFailed(),
                failedEvent
        );
        log.warn("Failed txId={}: [{}] {}", tx.getTransactionId(), code, reason);
    }

    private Transaction newTransaction(PaymentInitiatedEvent event) {
        Transaction tx = new Transaction();
        tx.setTransactionId(event.transactionId());
        tx.setSenderId(event.senderId());
        tx.setReceiverId(event.receiverId());
        tx.setAmount(event.amount());
        tx.setCurrency(event.currency());
        tx.setStatus(TransactionStatus.PROCESSING);
        return tx;
    }

    private Account openAccount(Long userId, String currency) {
        Account account = new Account();
        account.setUserId(userId);
        account.setCurrency(currency);
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }
}
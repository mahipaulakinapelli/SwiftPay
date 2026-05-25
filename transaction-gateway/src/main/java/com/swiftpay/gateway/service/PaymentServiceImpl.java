package com.swiftpay.gateway.service;

import com.swiftpay.gateway.enums.TransactionStatus;
import com.swiftpay.gateway.event.PaymentInitiatedEvent;
import com.swiftpay.gateway.cache.BalanceCacheReader;
import com.swiftpay.gateway.cache.IdempotencyService;
import com.swiftpay.gateway.config.TopicsProperties;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.entity.TransactionEntity;
import com.swiftpay.gateway.exception.BusinessException;
import com.swiftpay.gateway.exception.DuplicateTransactionException;
import com.swiftpay.gateway.mapper.PaymentMapper;
import com.swiftpay.gateway.outbox.OutboxEventService;
import com.swiftpay.gateway.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Default {@link PaymentService}: idempotency claim → DB save → outbox insert.
 *
 * <p>The transaction commits the {@code transactions} row and the
 * {@code outbox_events} row together; the {@code OutboxPublisher} drains the
 * outbox to Kafka asynchronously. This closes the dual-write window: there is
 * no path where the business row commits but the event is silently lost.</p>
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    static final String AGGREGATE_TYPE = "Payment";
    static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    private final TransactionRepository transactionRepository;
    private final PaymentMapper paymentMapper;
    private final OutboxEventService outboxEventService;
    private final IdempotencyService idempotencyService;
    private final BalanceCacheReader balanceCacheReader;
    private final TopicsProperties topics;

    /**
     * Constructor-based dependency injection. Spring auto-wires the single
     * declared constructor — no field or setter injection anywhere in the
     * service, so every collaborator the class needs is visible right here and
     * the instance is fully initialised when it leaves the constructor.
     */
    public PaymentServiceImpl(TransactionRepository transactionRepository,
                              PaymentMapper paymentMapper,
                              OutboxEventService outboxEventService,
                              IdempotencyService idempotencyService,
                              BalanceCacheReader balanceCacheReader,
                              TopicsProperties topics) {
        this.transactionRepository = transactionRepository;
        this.paymentMapper = paymentMapper;
        this.outboxEventService = outboxEventService;
        this.idempotencyService = idempotencyService;
        this.balanceCacheReader = balanceCacheReader;
        this.topics = topics;
    }

    /** {@inheritDoc}
     *
     * <p>Execution order:</p>
     * <ol>
     *   <li>Atomic Redis {@code SETNX} via {@link IdempotencyService} — duplicate {@code transaction_id} short-circuits before any DB write.</li>
     *   <li>{@link BalanceCacheReader} fast-fail pre-check — if the ledger's cached balance for {@code (sender_id, currency)} is below {@code amount}, reject with {@code 422 INSUFFICIENT_FUNDS} before touching the DB or Kafka. A cache miss is permissive: the ledger remains the source of truth and will emit {@code payment-failed} on actual insufficient funds.</li>
     *   <li>{@code INSERT} into {@code swiftpay_gateway.transactions} with {@link TransactionStatus#PENDING}.</li>
     *   <li>{@code INSERT} into {@code outbox_events} so the {@link PaymentInitiatedEvent} survives broker outages.</li>
     * </ol>
     */
    @Override
    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        if (!idempotencyService.tryClaim(request.transactionId())) {
            log.info("Rejecting duplicate transaction {}", request.transactionId());
            throw new DuplicateTransactionException(request.transactionId());
        }

        preCheckBalance(request);

        TransactionEntity entity = paymentMapper.toEntity(request, TransactionStatus.PENDING);
        TransactionEntity saved = transactionRepository.save(entity);
        log.info("Persisted transaction {} status={}", saved.getTransactionId(), saved.getStatus());

        PaymentInitiatedEvent event = paymentMapper.toEvent(saved);
        outboxEventService.record(
                AGGREGATE_TYPE,
                saved.getTransactionId().toString(),
                topics.paymentInitiated(),
                event
        );

        return paymentMapper.toResponse(saved);
    }

    /**
     * Fast-fail check against the ledger's shared Redis balance cache. Fail-open on
     * miss — the ledger is the source of truth.
     */
    private void preCheckBalance(PaymentRequest request) {
        Optional<BigDecimal> cached =
                balanceCacheReader.findBalance(request.senderId(), request.currency());
        if (cached.isEmpty()) {
            log.debug("Balance pre-check: no cache entry for sender={} currency={} — deferring to ledger",
                    request.senderId(), request.currency());
            return;
        }
        BigDecimal balance = cached.get();
        if (balance.compareTo(request.amount()) < 0) {
            log.info("Rejecting transaction {}: sender {} cached balance {} < amount {}",
                    request.transactionId(), request.senderId(), balance, request.amount());
            throw new BusinessException(
                    INSUFFICIENT_FUNDS,
                    "Sender balance " + balance + " " + request.currency()
                            + " is less than requested amount " + request.amount()
            );
        }
        log.debug("Balance pre-check OK: sender={} balance={} {} >= amount={}",
                request.senderId(), balance, request.currency(), request.amount());
    }
}

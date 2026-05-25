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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private TransactionRepository repository;
    @Mock private OutboxEventService outboxEventService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private BalanceCacheReader balanceCacheReader;

    private final PaymentMapper mapper = new PaymentMapper();
    private final TopicsProperties topics =
            new TopicsProperties("payment-initiated", "payment-completed", "payment-failed");

    private PaymentServiceImpl service;
    private PaymentRequest req;
    private UUID txId;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                repository, mapper, outboxEventService,
                idempotencyService, balanceCacheReader, topics);
        txId = UUID.randomUUID();
        req = new PaymentRequest(1L, 2L, new BigDecimal("10.00"), "USD", txId);
    }

    @Test
    void initiatePayment_happyPath_savesAndRecordsToOutbox() {
        when(idempotencyService.tryClaim(txId)).thenReturn(true);
        when(balanceCacheReader.findBalance(1L, "USD")).thenReturn(Optional.empty()); // cache miss → fail-open
        when(repository.save(any(TransactionEntity.class))).thenAnswer(inv -> {
            TransactionEntity e = inv.getArgument(0);
            e.setCreatedAt(OffsetDateTime.now());
            return e;
        });

        PaymentResponse resp = service.initiatePayment(req);

        assertThat(resp.transactionId()).isEqualTo(txId);
        assertThat(resp.status()).isEqualTo(TransactionStatus.PENDING);
        verify(repository, times(1)).save(any());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventService, times(1)).record(
                eq("Payment"),
                eq(txId.toString()),
                eq("payment-initiated"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).isInstanceOf(PaymentInitiatedEvent.class);
        PaymentInitiatedEvent event = (PaymentInitiatedEvent) payloadCaptor.getValue();
        assertThat(event.transactionId()).isEqualTo(txId);
        assertThat(event.status()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void initiatePayment_duplicate_throwsAndDoesNotSaveOrRecord() {
        when(idempotencyService.tryClaim(txId)).thenReturn(false);

        assertThatThrownBy(() -> service.initiatePayment(req))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining(txId.toString());

        verify(repository, never()).save(any());
        verify(outboxEventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void initiatePayment_cachedBalanceSufficient_proceeds() {
        when(idempotencyService.tryClaim(txId)).thenReturn(true);
        when(balanceCacheReader.findBalance(1L, "USD"))
                .thenReturn(Optional.of(new BigDecimal("100.00")));
        when(repository.save(any(TransactionEntity.class))).thenAnswer(inv -> {
            TransactionEntity e = inv.getArgument(0);
            e.setCreatedAt(OffsetDateTime.now());
            return e;
        });

        PaymentResponse resp = service.initiatePayment(req);

        assertThat(resp.status()).isEqualTo(TransactionStatus.PENDING);
        verify(repository, times(1)).save(any());
        verify(outboxEventService, times(1)).record(any(), any(), any(), any());
    }

    @Test
    void initiatePayment_cachedBalanceInsufficient_rejectsWithInsufficientFunds() {
        when(idempotencyService.tryClaim(txId)).thenReturn(true);
        when(balanceCacheReader.findBalance(1L, "USD"))
                .thenReturn(Optional.of(new BigDecimal("1.00"))); // less than request 10.00

        assertThatThrownBy(() -> service.initiatePayment(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("less than requested amount")
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo("INSUFFICIENT_FUNDS");

        // Confirm no DB write and no outbox event recorded once the pre-check fails.
        verify(repository, never()).save(any());
        verify(outboxEventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void initiatePayment_cacheMiss_isPermissive_andProceeds() {
        when(idempotencyService.tryClaim(txId)).thenReturn(true);
        when(balanceCacheReader.findBalance(1L, "USD")).thenReturn(Optional.empty());
        when(repository.save(any(TransactionEntity.class))).thenAnswer(inv -> {
            TransactionEntity e = inv.getArgument(0);
            e.setCreatedAt(OffsetDateTime.now());
            return e;
        });

        PaymentResponse resp = service.initiatePayment(req);

        assertThat(resp.status()).isEqualTo(TransactionStatus.PENDING);
        verify(outboxEventService, times(1)).record(any(), any(), any(), any());
    }
}
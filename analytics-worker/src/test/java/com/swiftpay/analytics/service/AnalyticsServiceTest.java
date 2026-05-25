package com.swiftpay.analytics.service;

import com.swiftpay.analytics.entity.AnalyticsTransaction;
import com.swiftpay.analytics.inbox.DuplicateEventException;
import com.swiftpay.analytics.inbox.InboxEnvelope;
import com.swiftpay.analytics.inbox.InboxService;
import com.swiftpay.analytics.repository.AnalyticsTransactionRepository;
import com.swiftpay.analytics.event.PaymentCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock AnalyticsTransactionRepository repository;
    @Mock InboxService inboxService;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(repository, inboxService);
    }

    @Test
    void project_newEvent_claimsInsertsAndMarksProcessed() {
        UUID txId = UUID.randomUUID();
        PaymentCompletedEvent e = PaymentCompletedEvent.of(
                txId, 1L, 2L, new BigDecimal("10.00"), "USD",
                new BigDecimal("90.00"), new BigDecimal("110.00"));
        InboxEnvelope env = envFor(e);

        when(repository.existsByTransactionId(txId)).thenReturn(false);

        service.project(env, e);

        verify(inboxService).claim(env);
        ArgumentCaptor<AnalyticsTransaction> capt = ArgumentCaptor.forClass(AnalyticsTransaction.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getTransactionId()).isEqualTo(txId);
        assertThat(capt.getValue().getAmount()).isEqualByComparingTo("10.00");
        assertThat(capt.getValue().getSenderBalanceAfter()).isEqualByComparingTo("90.00");
        verify(inboxService).markProcessed(env);
    }

    @Test
    void project_duplicateInbox_throws_noInsertNoMarkProcessed() {
        UUID txId = UUID.randomUUID();
        PaymentCompletedEvent e = PaymentCompletedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD",
                BigDecimal.ZERO, BigDecimal.ZERO);
        InboxEnvelope env = envFor(e);
        doThrow(new DuplicateEventException("dup")).when(inboxService).claim(env);

        assertThatThrownBy(() -> service.project(env, e))
                .isInstanceOf(DuplicateEventException.class);
        verify(repository, never()).save(any());
        verify(inboxService, never()).markProcessed(any());
    }

    @Test
    void project_projectionAlreadyHasRow_skipInsertButMarkProcessed() {
        UUID txId = UUID.randomUUID();
        PaymentCompletedEvent e = PaymentCompletedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD",
                BigDecimal.ZERO, BigDecimal.ZERO);
        InboxEnvelope env = envFor(e);
        when(repository.existsByTransactionId(txId)).thenReturn(true);

        service.project(env, e);

        verify(inboxService).claim(env);
        verify(repository, never()).save(any());
        verify(inboxService).markProcessed(env);
    }

    @Test
    void getVolume_delegates() {
        var summary = new AnalyticsTransactionRepository.VolumeSummary(3, new BigDecimal("42.00"));
        when(repository.getVolumeSummary()).thenReturn(summary);

        var got = service.getVolume();

        assertThat(got.totalTransactions()).isEqualTo(3);
        assertThat(got.totalVolume()).isEqualByComparingTo("42.00");
        verify(repository, times(1)).getVolumeSummary();
    }

    private static InboxEnvelope envFor(PaymentCompletedEvent e) {
        return new InboxEnvelope(
                e.eventId(), "analytics-worker", "payment-completed",
                0, 0L, PaymentCompletedEvent.class.getName(),
                e.transactionId().toString(), null);
    }
}
package com.swiftpay.analytics.kafka;

import com.swiftpay.analytics.inbox.DuplicateEventException;
import com.swiftpay.analytics.inbox.InboxEnvelope;
import com.swiftpay.analytics.service.AnalyticsService;
import com.swiftpay.analytics.event.PaymentCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventConsumerTest {

    @Mock AnalyticsService analyticsService;
    @InjectMocks AnalyticsEventConsumer consumer;

    @Test
    void onPaymentCompleted_delegatesToService_withCorrectEnvelope() {
        UUID txId = UUID.randomUUID();
        PaymentCompletedEvent e = PaymentCompletedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD",
                new BigDecimal("90"), new BigDecimal("110"));

        consumer.onPaymentCompleted(e, "payment-completed", 1, 0L);

        ArgumentCaptor<InboxEnvelope> envCaptor = ArgumentCaptor.forClass(InboxEnvelope.class);
        verify(analyticsService).project(envCaptor.capture(), eq(e));
        InboxEnvelope env = envCaptor.getValue();
        assertThat(env.eventId()).isEqualTo(e.eventId());
        assertThat(env.consumerGroup()).isEqualTo("analytics-worker");
        assertThat(env.topic()).isEqualTo("payment-completed");
        assertThat(env.partition()).isEqualTo(1);
        assertThat(env.aggregateId()).isEqualTo(txId.toString());
    }

    @Test
    void onPaymentCompleted_duplicate_isSwallowed() {
        UUID txId = UUID.randomUUID();
        PaymentCompletedEvent e = PaymentCompletedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD",
                BigDecimal.ZERO, BigDecimal.ZERO);
        doThrow(new DuplicateEventException("dup")).when(analyticsService).project(any(), eq(e));

        assertThatCode(() -> consumer.onPaymentCompleted(e, "payment-completed", 0, 0L))
                .doesNotThrowAnyException();
    }
}
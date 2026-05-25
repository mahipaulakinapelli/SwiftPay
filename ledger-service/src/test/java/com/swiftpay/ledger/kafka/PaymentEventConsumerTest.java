package com.swiftpay.ledger.kafka;

import com.swiftpay.ledger.enums.TransactionStatus;
import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.inbox.DuplicateEventException;
import com.swiftpay.ledger.inbox.InboxEnvelope;
import com.swiftpay.ledger.inbox.InboxService;
import com.swiftpay.ledger.service.LedgerProcessingService;
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
class PaymentEventConsumerTest {

    @Mock LedgerProcessingService ledgerProcessingService;
    @Mock InboxService inboxService;
    @InjectMocks PaymentEventConsumer consumer;

    @Test
    void onPaymentInitiated_delegatesToService_withCorrectEnvelope() {
        UUID txId = UUID.randomUUID();
        PaymentInitiatedEvent e = PaymentInitiatedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD", TransactionStatus.PENDING);

        consumer.onPaymentInitiated(e, "payment-initiated", 0, 5L);

        ArgumentCaptor<InboxEnvelope> envCaptor = ArgumentCaptor.forClass(InboxEnvelope.class);
        verify(ledgerProcessingService).process(envCaptor.capture(), eq(e));
        InboxEnvelope env = envCaptor.getValue();
        assertThat(env.eventId()).isEqualTo(e.eventId());
        assertThat(env.consumerGroup()).isEqualTo("ledger-service");
        assertThat(env.topic()).isEqualTo("payment-initiated");
        assertThat(env.partition()).isZero();
        assertThat(env.offset()).isEqualTo(5L);
        assertThat(env.aggregateId()).isEqualTo(txId.toString());
        assertThat(env.eventType()).isEqualTo(PaymentInitiatedEvent.class.getName());
    }

    @Test
    void onPaymentInitiated_duplicateEvent_isCaughtAndSwallowed() {
        UUID txId = UUID.randomUUID();
        PaymentInitiatedEvent e = PaymentInitiatedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD", TransactionStatus.PENDING);
        doThrow(new DuplicateEventException("dup")).when(ledgerProcessingService).process(any(), eq(e));

        assertThatCode(() -> consumer.onPaymentInitiated(e, "payment-initiated", 0, 7L))
                .doesNotThrowAnyException();
    }

    @Test
    void onDlt_writesFailedInboxRow() {
        UUID txId = UUID.randomUUID();
        PaymentInitiatedEvent e = PaymentInitiatedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD", TransactionStatus.PENDING);

        consumer.onDlt(e, "payment-initiated-dlt", 0, 0L);

        verify(inboxService).markFailed(any(InboxEnvelope.class), org.mockito.ArgumentMatchers.contains("DLT"));
    }
}
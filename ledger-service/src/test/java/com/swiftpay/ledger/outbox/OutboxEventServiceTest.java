package com.swiftpay.ledger.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.entity.OutboxEvent;
import com.swiftpay.ledger.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock OutboxEventRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private OutboxEventService service;

    @BeforeEach
    void setUp() {
        service = new OutboxEventService(repository, objectMapper);
    }

    @Test
    void record_persistsPendingRow_withCorrectMetadata() throws Exception {
        UUID txId = UUID.randomUUID();
        PaymentCompletedEvent payload = PaymentCompletedEvent.of(
                txId, 1L, 2L, new BigDecimal("100.00"), "USD",
                new BigDecimal("900.00"), new BigDecimal("1100.00"));

        service.record("Payment", txId.toString(), "payment-completed", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateType()).isEqualTo("Payment");
        assertThat(saved.getAggregateId()).isEqualTo(txId.toString());
        assertThat(saved.getEventType()).isEqualTo(PaymentCompletedEvent.class.getName());
        assertThat(saved.getTopic()).isEqualTo("payment-completed");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();

        PaymentCompletedEvent roundTrip = objectMapper.readValue(saved.getPayload(), PaymentCompletedEvent.class);
        assertThat(roundTrip.transactionId()).isEqualTo(txId);
        assertThat(roundTrip.senderBalanceAfter()).isEqualByComparingTo("900.00");
    }
}
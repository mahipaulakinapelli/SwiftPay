package com.swiftpay.gateway.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftpay.gateway.enums.TransactionStatus;
import com.swiftpay.gateway.event.PaymentInitiatedEvent;
import com.swiftpay.gateway.entity.OutboxEvent;
import com.swiftpay.gateway.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    void record_persistsPendingRow_withSerialisedPayload() throws Exception {
        UUID txId = UUID.randomUUID();
        PaymentInitiatedEvent payload = PaymentInitiatedEvent.of(
                txId, 1L, 2L, new BigDecimal("12.50"), "USD", TransactionStatus.PENDING);

        service.record("Payment", txId.toString(), "payment-initiated", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateType()).isEqualTo("Payment");
        assertThat(saved.getAggregateId()).isEqualTo(txId.toString());
        assertThat(saved.getEventType()).isEqualTo(PaymentInitiatedEvent.class.getName());
        assertThat(saved.getTopic()).isEqualTo("payment-initiated");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getProcessedAt()).isNull();

        PaymentInitiatedEvent roundTrip = objectMapper.readValue(saved.getPayload(), PaymentInitiatedEvent.class);
        assertThat(roundTrip.transactionId()).isEqualTo(txId);
        assertThat(roundTrip.amount()).isEqualByComparingTo("12.50");
        assertThat(roundTrip.occurredAt()).isNotNull().isBefore(OffsetDateTime.now().plusMinutes(1));
    }
}
package com.swiftpay.gateway.mapper;

import com.swiftpay.gateway.enums.TransactionStatus;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.entity.TransactionEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapper();

    @Test
    void toEntity_carriesAllFields() {
        UUID id = UUID.randomUUID();
        PaymentRequest req = new PaymentRequest(1L, 2L, new BigDecimal("100.0000"), "USD", id);

        TransactionEntity entity = mapper.toEntity(req, TransactionStatus.PENDING);

        assertThat(entity.getTransactionId()).isEqualTo(id);
        assertThat(entity.getSenderId()).isEqualTo(1L);
        assertThat(entity.getReceiverId()).isEqualTo(2L);
        assertThat(entity.getAmount()).isEqualByComparingTo("100.0000");
        assertThat(entity.getCurrency()).isEqualTo("USD");
        assertThat(entity.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void toResponse_carriesAllFields() {
        TransactionEntity e = new TransactionEntity();
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        e.setTransactionId(id);
        e.setSenderId(3L);
        e.setReceiverId(4L);
        e.setAmount(new BigDecimal("12.34"));
        e.setCurrency("EUR");
        e.setStatus(TransactionStatus.PENDING);
        e.setCreatedAt(now);

        var dto = mapper.toResponse(e);

        assertThat(dto.transactionId()).isEqualTo(id);
        assertThat(dto.senderId()).isEqualTo(3L);
        assertThat(dto.receiverId()).isEqualTo(4L);
        assertThat(dto.amount()).isEqualByComparingTo("12.34");
        assertThat(dto.currency()).isEqualTo("EUR");
        assertThat(dto.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(dto.acceptedAt()).isEqualTo(now);
    }

    @Test
    void toEvent_generatesEventIdAndOccurredAt() {
        TransactionEntity e = new TransactionEntity();
        e.setTransactionId(UUID.randomUUID());
        e.setSenderId(1L);
        e.setReceiverId(2L);
        e.setAmount(new BigDecimal("5.00"));
        e.setCurrency("USD");
        e.setStatus(TransactionStatus.PENDING);

        var event = mapper.toEvent(e);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.transactionId()).isEqualTo(e.getTransactionId());
        assertThat(event.amount()).isEqualByComparingTo("5.00");
        assertThat(event.status()).isEqualTo(TransactionStatus.PENDING);
    }
}
package com.swiftpay.ledger.mapper;

import com.swiftpay.ledger.enums.TransactionStatus;
import com.swiftpay.ledger.entity.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

    @Test
    void toHistoryItem_carriesAllFields() {
        Transaction t = new Transaction();
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        t.setTransactionId(id);
        t.setSenderId(1L); t.setReceiverId(2L);
        t.setAmount(new BigDecimal("10.5000")); t.setCurrency("USD");
        t.setStatus(TransactionStatus.COMPLETED);
        t.setCreatedAt(now); t.setUpdatedAt(now);

        var dto = new TransactionMapper().toHistoryItem(t);

        assertThat(dto.transactionId()).isEqualTo(id);
        assertThat(dto.senderId()).isEqualTo(1L);
        assertThat(dto.receiverId()).isEqualTo(2L);
        assertThat(dto.amount()).isEqualByComparingTo("10.5000");
        assertThat(dto.currency()).isEqualTo("USD");
        assertThat(dto.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(dto.createdAt()).isEqualTo(now);
        assertThat(dto.updatedAt()).isEqualTo(now);
    }
}
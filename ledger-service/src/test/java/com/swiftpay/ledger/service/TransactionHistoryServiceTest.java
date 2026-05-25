package com.swiftpay.ledger.service;

import com.swiftpay.ledger.enums.TransactionStatus;
import com.swiftpay.ledger.response.PagedResponse;
import com.swiftpay.ledger.dto.TransactionHistoryItem;
import com.swiftpay.ledger.entity.Transaction;
import com.swiftpay.ledger.mapper.TransactionMapper;
import com.swiftpay.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock TransactionRepository transactionRepository;
    TransactionMapper mapper = new TransactionMapper();

    @Test
    void getHistory_wrapsRepositoryPage() {
        TransactionHistoryService service = new TransactionHistoryService(transactionRepository, mapper);

        Transaction t = new Transaction();
        t.setTransactionId(UUID.randomUUID());
        t.setSenderId(1L); t.setReceiverId(2L);
        t.setAmount(new BigDecimal("10.00")); t.setCurrency("USD");
        t.setStatus(TransactionStatus.COMPLETED);
        t.setCreatedAt(OffsetDateTime.now()); t.setUpdatedAt(OffsetDateTime.now());

        Pageable pageable = PageRequest.of(0, 5);
        when(transactionRepository.findHistoryForUser(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(t), pageable, 1));

        PagedResponse<TransactionHistoryItem> page = service.getHistory(1L, pageable);

        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrev()).isFalse();
    }
}
package com.swiftpay.ledger.mapper;

import com.swiftpay.ledger.dto.TransactionHistoryItem;
import com.swiftpay.ledger.entity.Transaction;
import org.springframework.stereotype.Component;

/**
 * Hand-written conversion from the JPA {@link Transaction} entity to the
 * history response DTO. Kept explicit so a schema-side rename forces a
 * mapping update at compile time.
 */
@Component
public class TransactionMapper {

    /** Build a history row DTO from a persisted transaction. */
    public TransactionHistoryItem toHistoryItem(Transaction tx) {
        return new TransactionHistoryItem(
                tx.getTransactionId(),
                tx.getSenderId(),
                tx.getReceiverId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );
    }
}
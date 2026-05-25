package com.swiftpay.ledger.service;

import com.swiftpay.ledger.response.PagedResponse;
import com.swiftpay.ledger.dto.TransactionHistoryItem;
import com.swiftpay.ledger.entity.Transaction;
import com.swiftpay.ledger.mapper.TransactionMapper;
import com.swiftpay.ledger.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only service backing {@code GET /v1/transactions/{userId}}.
 *
 * <p>{@code readOnly = true} lets Hibernate skip dirty-checking on the
 * end-of-transaction flush — a small but free win on pure read paths.</p>
 */
@Service
public class TransactionHistoryService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    /** Constructor-based dependency injection. */
    public TransactionHistoryService(TransactionRepository transactionRepository,
                                     TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    /**
     * Fetch a paginated history for a user (sender or receiver).
     *
     * @param userId   the user to filter on
     * @param pageable page index, size, and sort
     * @return mapped {@link TransactionHistoryItem}s wrapped in a {@link PagedResponse}
     */
    @Transactional(readOnly = true)
    public PagedResponse<TransactionHistoryItem> getHistory(Long userId, Pageable pageable) {
        Page<Transaction> page = transactionRepository.findHistoryForUser(userId, pageable);
        return PagedResponse.of(
                page.getContent().stream().map(transactionMapper::toHistoryItem).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }
}

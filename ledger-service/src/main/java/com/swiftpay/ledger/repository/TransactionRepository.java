package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data repository for the ledger's {@link Transaction} table.
 *
 * <p>PK is the cross-service {@link UUID} — same value the gateway used and
 * the Kafka event key carried.</p>
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Paginated history for a user, matching either side of the transfer.
     *
     * <p>Hand-rolled JPQL (instead of a derived query like
     * {@code findBySenderIdOr...}) so the call site passes the user id once
     * rather than twice.</p>
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.senderId = :userId OR t.receiverId = :userId
            """)
    Page<Transaction> findHistoryForUser(@Param("userId") Long userId, Pageable pageable);
}
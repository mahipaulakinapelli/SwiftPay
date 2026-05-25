package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Account}.
 *
 * <p>Accounts are keyed by surrogate id but most lookups are by
 * {@code (user_id, currency)} — which is also the natural-key UNIQUE
 * constraint in the schema.</p>
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Look up a specific account for a user and currency. Used by the ledger
     * to find the sender's debit-eligible account and the receiver's credit
     * destination.
     */
    Optional<Account> findByUserIdAndCurrency(Long userId, String currency);

    /**
     * Load every account belonging to a user, sorted by currency for stable
     * output order — used by {@code BalanceCacheService} when populating
     * the cache on miss.
     */
    List<Account> findByUserIdOrderByCurrencyAsc(Long userId);
}
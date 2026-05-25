package com.swiftpay.ledger.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link BalancesChangedEvent} and evicts the corresponding
 * Redis cache entries — but only <em>after</em> the originating transaction
 * has committed.
 *
 * <p>Moving the eviction to {@link TransactionPhase#AFTER_COMMIT} closes
 * two ordering hazards present when the {@code DEL} ran inside the
 * transaction:</p>
 * <ol>
 *   <li>Transaction rolled back, cache wiped → next read repopulates from
 *       the rolled-back snapshot (false positive). With AFTER_COMMIT we
 *       simply don't fire on rollback.</li>
 *   <li>Transaction committed, then Redis {@code DEL} failed inside the
 *       same code path → cache stays stale until TTL expiry. We still log
 *       and swallow Redis errors here so the commit isn't surfaced as a
 *       failure to the consumer; the 5-minute TTL on the cache itself
 *       remains the long-stop safety net.</li>
 * </ol>
 */
@Component
public class BalanceCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(BalanceCacheInvalidator.class);

    private final BalanceCacheService balanceCacheService;

    /** Constructor-based dependency injection. */
    public BalanceCacheInvalidator(BalanceCacheService balanceCacheService) {
        this.balanceCacheService = balanceCacheService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBalancesChanged(BalancesChangedEvent event) {
        invalidateSafely(event.senderId());
        if (event.receiverId() != null && !event.receiverId().equals(event.senderId())) {
            invalidateSafely(event.receiverId());
        }
    }

    private void invalidateSafely(Long userId) {
        try {
            balanceCacheService.invalidate(userId);
        } catch (RuntimeException ex) {
            // TTL (5 min) is the safety net — log and continue rather than fail the listener chain.
            log.warn("Post-commit cache invalidation failed for userId={} — TTL will heal", userId, ex);
        }
    }
}
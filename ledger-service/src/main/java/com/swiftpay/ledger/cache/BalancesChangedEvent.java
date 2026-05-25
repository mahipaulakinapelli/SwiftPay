package com.swiftpay.ledger.cache;

/**
 * In-process domain event signalling that one or more user balances have
 * been mutated by a settlement transaction.
 *
 * <p>Published synchronously by {@code LedgerProcessingService} while still
 * inside the {@code @Transactional} boundary, but only acted on by
 * {@link BalanceCacheInvalidator} after the transaction's commit phase.
 * The split is what makes the cache eviction safe: we never evict for
 * balances that were rolled back, and we never skip eviction for balances
 * that did commit.</p>
 *
 * @param senderId   user id whose balance was debited
 * @param receiverId user id whose balance was credited (may equal sender on edge cases)
 */
public record BalancesChangedEvent(Long senderId, Long receiverId) {
}
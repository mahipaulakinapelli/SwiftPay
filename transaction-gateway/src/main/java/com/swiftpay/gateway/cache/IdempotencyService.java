package com.swiftpay.gateway.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Atomic transaction-id reservation via Redis {@code SET … NX EX}.
 *
 * <p>Idempotency is the contract: "for a given {@code transaction_id}, accept
 * at most one POST within the TTL window". A client that retries after a
 * network blip is safe; a client that maliciously replays gets a 409.</p>
 *
 * <h2>Why {@code SET NX EX} and not {@code GET}-then-{@code SET}</h2>
 * The two-step pattern has a TOCTOU race: two concurrent requests both see
 * "not present", both proceed. {@code SET NX} ({@code setIfAbsent}) is the
 * single atomic Redis command that decides the winner.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /** Common prefix for all idempotency keys — useful for namespace inspection. */
    static final String KEY_PREFIX = "swiftpay:idempotency:tx:";

    /** Claim window. After this many hours the same {@code transaction_id} can be retried. */
    static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /** Constructor-based dependency injection. */
    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Atomically reserve a {@code transaction_id} for the calling request.
     *
     * @param transactionId the client-supplied id we're trying to claim
     * @return {@code true} if the caller now owns this id (proceed with the payment),
     *         {@code false} if another caller already claimed it within the TTL window
     */
    public boolean tryClaim(UUID transactionId) {
        String key = keyFor(transactionId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, OffsetDateTime.now().toString(), TTL);
        boolean ok = Boolean.TRUE.equals(acquired);
        log.debug("Idempotency claim for {} -> {}", transactionId, ok ? "ACQUIRED" : "DUPLICATE");
        return ok;
    }

    /** Computes the Redis key for a transaction id — exposed package-private for tests / inspection. */
    public static String keyFor(UUID transactionId) {
        return KEY_PREFIX + transactionId;
    }
}

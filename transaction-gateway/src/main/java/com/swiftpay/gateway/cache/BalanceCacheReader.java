package com.swiftpay.gateway.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only view over the per-user balance cache populated by {@code ledger-service}'s
 * {@code BalanceCacheService}. Used by the gateway for a <em>fast-fail</em> insufficient-funds
 * pre-check before persisting a payment.
 *
 * <h2>Wire contract</h2>
 * <p>Redis key: {@code swiftpay:balance:user:<userId>} (defined here and on the ledger
 * side independently — agreed by convention, not by shared code). The cached value is a
 * JSON array of {@code {account_id, currency, balance, status}} entries.</p>
 *
 * <h2>Why fail-open on cache miss</h2>
 * <p>The ledger is the source of truth — this reader is a latency optimization. A miss
 * (no Redis entry yet, JSON parse failure, Redis temporarily unreachable) returns
 * {@link Optional#empty()} and lets the gateway accept the request; the ledger will
 * still authoritatively check and emit {@code payment-failed} if the balance is actually
 * insufficient. Refusing payments on cache misses would surface every cache cold-start as
 * a customer-visible 422.</p>
 */
@Service
public class BalanceCacheReader {

    private static final Logger log = LoggerFactory.getLogger(BalanceCacheReader.class);

    /** Same key prefix the ledger writes — wire contract. */
    static final String KEY_PREFIX = "swiftpay:balance:user:";

    private static final TypeReference<List<Map<String, Object>>> CACHED_BALANCES =
            new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Constructor-based dependency injection. */
    public BalanceCacheReader(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Look up the cached balance for a given {@code (userId, currency)} pair.
     *
     * @param userId   the user whose balance to check
     * @param currency ISO-4217 currency code
     * @return the cached balance if the entry exists and matches, otherwise {@link Optional#empty()}
     */
    public Optional<BigDecimal> findBalance(Long userId, String currency) {
        String key = KEY_PREFIX + userId;
        String cached;
        try {
            cached = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException ex) {
            log.warn("Balance-cache read failed for user={} currency={} ({}) — treating as miss",
                    userId, currency, ex.toString());
            return Optional.empty();
        }
        if (cached == null) {
            log.debug("Balance-cache MISS  key={} (cold cache or evicted)", key);
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> entries = objectMapper.readValue(cached, CACHED_BALANCES);
            return entries.stream()
                    .filter(e -> currency.equalsIgnoreCase(asString(e.get("currency"))))
                    .map(e -> toBigDecimal(e.get("balance")))
                    .filter(java.util.Objects::nonNull)
                    .findFirst();
        } catch (Exception ex) {
            log.warn("Balance-cache parse failed for key={} ({}) — treating as miss", key, ex.toString());
            return Optional.empty();
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(o.toString());
    }
}

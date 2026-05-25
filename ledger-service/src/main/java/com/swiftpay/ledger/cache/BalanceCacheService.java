package com.swiftpay.ledger.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledger.dto.BalanceDto;
import com.swiftpay.ledger.entity.Account;
import com.swiftpay.ledger.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Cache-aside layer for per-user account balances.
 *
 * <h2>Read path</h2>
 * <pre>
 *   GET balances ─▶ Redis ─┬─ HIT  ─▶ deserialize and return
 *                          └─ MISS ─▶ SELECT FROM accounts
 *                                     SET Redis key (TTL 5 min)
 *                                     return
 * </pre>
 *
 * <h2>Write path (called from {@code LedgerProcessingService})</h2>
 * <p>{@link #invalidate} after every successful transfer for both sender
 * and receiver. The 5-minute TTL is a safety net in case invalidation is
 * skipped (e.g. process crash between commit and DEL).</p>
 */
@Service
public class BalanceCacheService {

    private static final Logger log = LoggerFactory.getLogger(BalanceCacheService.class);

    /** Keyspace prefix — useful for {@code redis-cli KEYS} inspection. */
    static final String KEY_PREFIX = "swiftpay:balance:user:";

    /** Worst-case staleness on a missed invalidation. */
    static final Duration TTL = Duration.ofMinutes(5);

    private static final TypeReference<List<BalanceDto>> LIST_OF_BALANCES = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    /** Constructor-based dependency injection. */
    public BalanceCacheService(StringRedisTemplate redisTemplate,
                               AccountRepository accountRepository,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Read all of a user's balances, hitting Redis first and falling back to
     * Postgres on miss. Populates the cache on miss so subsequent reads hit.
     *
     * @param userId the user whose balances to load
     * @return one {@link BalanceDto} per (user × currency) account, ordered by currency
     */
    public List<BalanceDto> getBalances(Long userId) {
        String key = keyFor(userId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.info("Cache HIT  key={}", key);
            return deserialize(cached);
        }
        log.info("Cache MISS key={} — loading from Postgres", key);
        List<BalanceDto> balances = accountRepository.findByUserIdOrderByCurrencyAsc(userId).stream()
                .map(this::toDto)
                .toList();
        redisTemplate.opsForValue().set(key, serialize(balances), TTL);
        log.info("Cache FILL key={} entries={} ttl={}s", key, balances.size(), TTL.toSeconds());
        return balances;
    }

    /**
     * Evict a user's cached balances. Called after any balance-mutating
     * operation so the next read picks up the fresh DB state.
     *
     * @param userId user whose cache entry to delete
     */
    public void invalidate(Long userId) {
        String key = keyFor(userId);
        Boolean deleted = redisTemplate.delete(key);
        log.info("Cache INVALIDATE key={} existed={}", key, Boolean.TRUE.equals(deleted));
    }

    /** Computes the Redis key for a given user — exposed for tests / ops inspection. */
    public static String keyFor(Long userId) {
        return KEY_PREFIX + userId;
    }

    private BalanceDto toDto(Account a) {
        return new BalanceDto(a.getId(), a.getCurrency(), a.getBalance(), a.getStatus());
    }

    private String serialize(List<BalanceDto> balances) {
        try {
            return objectMapper.writeValueAsString(balances);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize balances", e);
        }
    }

    private List<BalanceDto> deserialize(String json) {
        try {
            return objectMapper.readValue(json, LIST_OF_BALANCES);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize cached balances", e);
        }
    }
}
package com.swiftpay.gateway.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceCacheReaderTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> ops;

    private BalanceCacheReader reader;

    @BeforeEach
    void setUp() {
        // Mirror the application's snake-case Jackson config so the reader sees
        // the same JSON shape the ledger writes.
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        reader = new BalanceCacheReader(redisTemplate, mapper);
    }

    @Test
    void findBalance_returnsBalance_whenCacheHitAndCurrencyMatches() {
        String json = """
                [
                  {"account_id": 1, "currency": "USD", "balance": 150.4321, "status": "ACTIVE"},
                  {"account_id": 2, "currency": "EUR", "balance":  10.0000, "status": "ACTIVE"}
                ]
                """;
        when(ops.get("swiftpay:balance:user:1")).thenReturn(json);

        Optional<BigDecimal> usd = reader.findBalance(1L, "USD");
        Optional<BigDecimal> eur = reader.findBalance(1L, "EUR");

        assertThat(usd).isPresent();
        assertThat(usd.get()).isEqualByComparingTo("150.4321");
        assertThat(eur).isPresent();
        assertThat(eur.get()).isEqualByComparingTo("10.0000");
    }

    @Test
    void findBalance_returnsEmpty_whenCacheMiss() {
        when(ops.get("swiftpay:balance:user:42")).thenReturn(null);

        assertThat(reader.findBalance(42L, "USD")).isEmpty();
    }

    @Test
    void findBalance_returnsEmpty_whenCurrencyAbsent() {
        when(ops.get("swiftpay:balance:user:1")).thenReturn(
                "[{\"account_id\":1,\"currency\":\"USD\",\"balance\":50.0,\"status\":\"ACTIVE\"}]");

        assertThat(reader.findBalance(1L, "INR")).isEmpty();
    }

    @Test
    void findBalance_returnsEmpty_whenJsonMalformed() {
        when(ops.get("swiftpay:balance:user:1")).thenReturn("not-json");

        // Parse failure must be permissive — let the ledger handle authoritatively.
        assertThat(reader.findBalance(1L, "USD")).isEmpty();
    }

    @Test
    void findBalance_returnsEmpty_whenRedisUnreachable() {
        when(ops.get("swiftpay:balance:user:1"))
                .thenThrow(new RedisConnectionFailureException("simulated"));

        assertThat(reader.findBalance(1L, "USD")).isEmpty();
    }

    @Test
    void findBalance_currencyMatchIsCaseInsensitive() {
        when(ops.get("swiftpay:balance:user:1")).thenReturn(
                "[{\"account_id\":1,\"currency\":\"USD\",\"balance\":75,\"status\":\"ACTIVE\"}]");

        assertThat(reader.findBalance(1L, "usd")).isPresent();
    }
}

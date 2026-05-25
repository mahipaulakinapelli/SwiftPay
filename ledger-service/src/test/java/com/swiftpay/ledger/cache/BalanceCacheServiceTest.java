package com.swiftpay.ledger.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftpay.ledger.enums.AccountStatus;
import com.swiftpay.ledger.dto.BalanceDto;
import com.swiftpay.ledger.entity.Account;
import com.swiftpay.ledger.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceCacheServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AccountRepository accountRepository;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    BalanceCacheService service;

    @BeforeEach
    void setUp() {
        service = new BalanceCacheService(redisTemplate, accountRepository, objectMapper);
    }

    @Test
    void getBalances_cacheMiss_loadsFromDbAndFills() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("swiftpay:balance:user:1")).thenReturn(null);
        when(accountRepository.findByUserIdOrderByCurrencyAsc(1L)).thenReturn(List.of(account(11L, "USD", "100.00")));

        List<BalanceDto> result = service.getBalances(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currency()).isEqualTo("USD");
        verify(valueOps).set(eq("swiftpay:balance:user:1"), any(String.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    void getBalances_cacheHit_skipsDb() {
        String cached = """
                [{"account_id":11,"currency":"USD","balance":100.00,"status":"ACTIVE"}]
                """.strip();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("swiftpay:balance:user:1")).thenReturn(cached);

        List<BalanceDto> result = service.getBalances(1L);

        assertThat(result).hasSize(1);
        verify(accountRepository, never()).findByUserIdOrderByCurrencyAsc(any());
        verify(valueOps, never()).set(any(), any(), any());
    }

    @Test
    void invalidate_deletesKey() {
        when(redisTemplate.delete("swiftpay:balance:user:1")).thenReturn(true);
        service.invalidate(1L);
        verify(redisTemplate, times(1)).delete("swiftpay:balance:user:1");
    }

    private Account account(long id, String currency, String balance) {
        Account a = new Account();
        a.setId(id);
        a.setUserId(1L);
        a.setCurrency(currency);
        a.setBalance(new BigDecimal(balance));
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }
}
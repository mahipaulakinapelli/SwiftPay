package com.swiftpay.ledger.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BalanceCacheInvalidatorTest {

    @Mock BalanceCacheService balanceCacheService;
    @InjectMocks BalanceCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        // no-op
    }

    @Test
    void invalidatesBothSenderAndReceiver_whenDistinct() {
        invalidator.onBalancesChanged(new BalancesChangedEvent(1L, 2L));

        verify(balanceCacheService).invalidate(1L);
        verify(balanceCacheService).invalidate(2L);
    }

    @Test
    void invalidatesOnce_whenSenderEqualsReceiver() {
        invalidator.onBalancesChanged(new BalancesChangedEvent(1L, 1L));

        verify(balanceCacheService, times(1)).invalidate(1L);
    }

    @Test
    void skipsReceiverInvalidation_whenReceiverNull() {
        invalidator.onBalancesChanged(new BalancesChangedEvent(1L, null));

        verify(balanceCacheService, times(1)).invalidate(1L);
    }

    @Test
    void swallowsRedisExceptionsSoOtherListenersDoNotBreak() {
        doThrow(new RuntimeException("redis down")).when(balanceCacheService).invalidate(1L);

        invalidator.onBalancesChanged(new BalancesChangedEvent(1L, 2L));

        verify(balanceCacheService).invalidate(1L);
        verify(balanceCacheService).invalidate(2L);
    }

    @Test
    void onlyInvalidatesSender_whenReceiverInvalidationThrowsButSenderSucceeds() {
        doThrow(new RuntimeException("redis hiccup")).when(balanceCacheService).invalidate(2L);

        invalidator.onBalancesChanged(new BalancesChangedEvent(1L, 2L));

        verify(balanceCacheService).invalidate(1L);
        verify(balanceCacheService).invalidate(2L);
        verify(balanceCacheService, never()).invalidate(99L);
    }
}
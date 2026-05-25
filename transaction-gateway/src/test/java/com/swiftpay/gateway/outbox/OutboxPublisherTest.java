package com.swiftpay.gateway.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock OutboxPublishWorker worker;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(worker);
        ReflectionTestUtils.setField(publisher, "batchSize", 50);
        ReflectionTestUtils.setField(publisher, "sendTimeoutMs", 1000L);
        ReflectionTestUtils.setField(publisher, "maxRetryCount", 3);
        ReflectionTestUtils.setField(publisher, "maxBackoffSeconds", 300);
        ReflectionTestUtils.setField(publisher, "stuckPublishingThresholdSeconds", 60);
    }

    @Test
    void publishBatch_finalizesEachClaimedRow() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(worker.claimBatch(50, 300)).thenReturn(List.of(a, b));

        publisher.publishBatch();

        verify(worker).claimBatch(50, 300);
        verify(worker).finalizeOne(a, 1000L, 3);
        verify(worker).finalizeOne(b, 1000L, 3);
    }

    @Test
    void publishBatch_emptyClaim_skipsFinalize() {
        when(worker.claimBatch(50, 300)).thenReturn(List.of());
        publisher.publishBatch();
        verify(worker, never()).finalizeOne(any(), anyLong(), anyInt());
    }

    @Test
    void rescueStuckPublishing_invokesWorker() {
        when(worker.rescueStuckPublishing(60)).thenReturn(2);
        publisher.rescueStuckPublishing();
        verify(worker, times(1)).rescueStuckPublishing(60);
    }

    // Bare-bones any* helpers to keep the test imports tidy
    private static UUID any() { return org.mockito.ArgumentMatchers.any(); }
    private static long anyLong() { return org.mockito.ArgumentMatchers.anyLong(); }
    private static int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }
}
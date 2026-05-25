package com.swiftpay.ledger.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.entity.OutboxEvent;
import com.swiftpay.ledger.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublishWorkerTest {

    @Mock OutboxEventRepository repository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private OutboxPublishWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxPublishWorker(repository, kafkaTemplate, objectMapper);
    }

    @Test
    void claimBatch_flipsToPublishing_setsLastAttemptAt() {
        OutboxEvent row = newPendingRow();
        when(repository.claimPendingForPublish(50, 300)).thenReturn(List.of(row));

        List<UUID> ids = worker.claimBatch(50, 300);

        assertThat(ids).containsExactly(row.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
        assertThat(row.getLastAttemptAt()).isNotNull();
    }

    @Test
    void finalizeOne_marksPublished_onAck() {
        OutboxEvent row = newPendingRow();
        row.setStatus(OutboxStatus.PUBLISHING);
        when(repository.findOneById(row.getId())).thenReturn(row);

        @SuppressWarnings({"unchecked", "rawtypes"})
        SendResult<String, Object> sendResult = new SendResult(null,
                new RecordMetadata(new TopicPartition("payment-completed", 0), 0L, 0, 0L, 0, 0));
        when(kafkaTemplate.send(eq("payment-completed"), eq(row.getAggregateId()), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        worker.finalizeOne(row.getId(), 1000L, 3);

        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(row.getProcessedAt()).isNotNull();
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void finalizeOne_failureBelowBudget_goesToFailed() {
        OutboxEvent row = newPendingRow();
        row.setStatus(OutboxStatus.PUBLISHING);
        when(repository.findOneById(row.getId())).thenReturn(row);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        worker.finalizeOne(row.getId(), 1000L, 3);

        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getLastError()).contains("broker down");
    }

    @Test
    void finalizeOne_failureAtBudget_goesToDeadLettered() {
        OutboxEvent row = newPendingRow();
        row.setStatus(OutboxStatus.PUBLISHING);
        row.setRetryCount(2);
        when(repository.findOneById(row.getId())).thenReturn(row);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("still failing")));

        worker.finalizeOne(row.getId(), 1000L, 3);

        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTERED);
        assertThat(row.getRetryCount()).isEqualTo(3);
        assertThat(row.getProcessedAt()).isNotNull();
    }

    @Test
    void finalizeOne_poisonPayload_unknownClass_goesStraightToDeadLettered() {
        OutboxEvent row = newPendingRow();
        row.setStatus(OutboxStatus.PUBLISHING);
        row.setEventType("com.does.not.Exist");
        when(repository.findOneById(row.getId())).thenReturn(row);

        worker.finalizeOne(row.getId(), 1000L, 3);

        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTERED);
        assertThat(row.getLastError()).contains("poison-payload");
    }

    @Test
    void finalizeOne_rowNotInPublishing_skips() {
        OutboxEvent row = newPendingRow();
        row.setStatus(OutboxStatus.PENDING);
        when(repository.findOneById(row.getId())).thenReturn(row);
        worker.finalizeOne(row.getId(), 1000L, 3);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void rescueStuckPublishing_delegates() {
        when(repository.rescueStuckPublishing(60)).thenReturn(2);
        assertThat(worker.rescueStuckPublishing(60)).isEqualTo(2);
    }

    private OutboxEvent newPendingRow() {
        OutboxEvent e = new OutboxEvent();
        UUID txId = UUID.randomUUID();
        e.setId(UUID.randomUUID());
        e.setAggregateType("Payment");
        e.setAggregateId(txId.toString());
        e.setEventType(PaymentCompletedEvent.class.getName());
        e.setTopic("payment-completed");
        e.setPayload(asJson(PaymentCompletedEvent.of(
                txId, 1L, 2L, new BigDecimal("1.00"), "USD",
                new BigDecimal("99.00"), new BigDecimal("101.00"))));
        e.setStatus(OutboxStatus.PENDING);
        e.setRetryCount(0);
        e.setCreatedAt(OffsetDateTime.now());
        return e;
    }

    private String asJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
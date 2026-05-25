package com.swiftpay.ledger.kafka;

import com.swiftpay.ledger.enums.TransactionStatus;
import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.event.PaymentFailedEvent;
import com.swiftpay.ledger.config.TopicsProperties;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    PaymentEventProducer producer;

    @BeforeEach
    void setUp() {
        TopicsProperties topics = new TopicsProperties(
                "payment-initiated", "payment-completed", "payment-failed");
        producer = new PaymentEventProducer(kafkaTemplate, topics);
    }

    @Test
    void publishCompleted_sendsToCompletedTopicWithTxIdKey() {
        UUID txId = UUID.randomUUID();
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD",
                new BigDecimal("90"), new BigDecimal("110"));

        RecordMetadata md = new RecordMetadata(new TopicPartition("payment-completed", 0),
                0, 0, 0, 0, 0);
        when(kafkaTemplate.send(eq("payment-completed"), eq(txId.toString()), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new SendResult<>(null, md)));

        producer.publishPaymentCompleted(event);

        ArgumentCaptor<Object> capt = ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(kafkaTemplate)
                .send(eq("payment-completed"), eq(txId.toString()), capt.capture());
        assertThat(capt.getValue()).isSameAs(event);
    }

    @Test
    void publishFailed_sendsToFailedTopic() {
        UUID txId = UUID.randomUUID();
        PaymentFailedEvent event = PaymentFailedEvent.of(
                txId, 1L, 2L, new BigDecimal("10"), "USD",
                "INSUFFICIENT_FUNDS", "low balance");

        when(kafkaTemplate.send(eq("payment-failed"), eq(txId.toString()), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        producer.publishPaymentFailed(event);

        org.mockito.Mockito.verify(kafkaTemplate)
                .send(eq("payment-failed"), eq(txId.toString()), eq(event));
    }
}
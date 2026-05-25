package com.swiftpay.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import com.swiftpay.gateway.repository.TransactionRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:14-alpine"))
                    .withDatabaseName("swiftpay_gateway")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static RedisContainer redis =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // Postgres
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Redis — clear ACL creds from app.yml (vanilla container has no ACL)
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.data.redis.username", () -> "");
        r.add("spring.data.redis.password", () -> "");
        // Kafka
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TransactionRepository transactionRepository;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void test1_successfulPayment_persists_and_publishes_event() throws Exception {
        UUID txId = UUID.randomUUID();

        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(txId, 1L, 2L, "10.0000", "USD")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(transactionRepository.findById(txId)).isPresent());

        assertThat(redisTemplate.hasKey("swiftpay:idempotency:tx:" + txId)).isTrue();

        try (KafkaConsumer<String, String> consumer = newConsumer("verify-success")) {
            consumer.subscribe(List.of("payment-initiated"));
            ConsumerRecord<String, String> rec = pollFor(consumer, txId.toString());
            assertThat(rec).isNotNull();
            JsonNode v = objectMapper.readTree(rec.value());
            assertThat(v.get("transaction_id").asText()).isEqualTo(txId.toString());
            assertThat(v.get("status").asText()).isEqualTo("PENDING");
        }
    }

    @Test
    void test3_duplicateTransaction_returns409_and_persists_only_once() throws Exception {
        UUID txId = UUID.randomUUID();
        String body = paymentBody(txId, 1L, 2L, "5.0000", "USD");

        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_TRANSACTION"));

        assertThat(transactionRepository.findAll().stream()
                .filter(t -> t.getTransactionId().equals(txId))
                .count()).isEqualTo(1L);
    }

    @Test
    void test5_kafkaFlow_eventCarriesFullPayload() throws Exception {
        UUID txId = UUID.randomUUID();
        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(txId, 3L, 4L, "99.9900", "EUR")))
                .andExpect(status().isAccepted());

        try (KafkaConsumer<String, String> consumer = newConsumer("verify-flow")) {
            consumer.subscribe(List.of("payment-initiated"));
            ConsumerRecord<String, String> rec = pollFor(consumer, txId.toString());
            assertThat(rec).isNotNull();
            JsonNode v = objectMapper.readTree(rec.value());
            assertThat(v.get("sender_id").asLong()).isEqualTo(3);
            assertThat(v.get("receiver_id").asLong()).isEqualTo(4);
            assertThat(v.get("currency").asText()).isEqualTo("EUR");
            assertThat(new java.math.BigDecimal(v.get("amount").asText()))
                    .isEqualByComparingTo("99.9900");
            // Wire-format type header is the LOGICAL name (not the Java FQN), so
            // consumers in other repos don't depend on our class layout.
            String typeHeader = new String(rec.headers().lastHeader("__TypeId__").value());
            assertThat(typeHeader).isEqualTo("payment-initiated");
        }
    }

    private String paymentBody(UUID txId, long sender, long receiver, String amount, String currency) {
        return """
                {
                  "sender_id": %d, "receiver_id": %d,
                  "amount": %s, "currency": "%s",
                  "transaction_id": "%s"
                }
                """.formatted(sender, receiver, amount, currency, txId);
    }

    private KafkaConsumer<String, String> newConsumer(String groupId) {
        Map<String, Object> p = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), groupId, "true");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(p);
    }

    private ConsumerRecord<String, String> pollFor(KafkaConsumer<String, String> consumer, String key) {
        return await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).until(() -> {
            var records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records) {
                if (key.equals(r.key())) return r;
            }
            return null;
        }, r -> r != null);
    }
}
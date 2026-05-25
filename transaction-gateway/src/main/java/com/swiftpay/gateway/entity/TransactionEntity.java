package com.swiftpay.gateway.entity;

import com.swiftpay.gateway.enums.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Gateway-side projection of a payment request.
 *
 * <p>This is the gateway's own {@code transactions} table — distinct from the
 * ledger's settled-transaction table. It records "we accepted this request"
 * before any Kafka publish, so a power loss between persist and publish
 * doesn't leave us in the dark.</p>
 *
 * <p>The {@code transactionId} is client-supplied (not generated), so we
 * deliberately omit {@code @GeneratedValue}; Spring Data sees a null
 * {@code @Version} on first {@code save()} and routes to {@code persist()}
 * rather than {@code merge()}.</p>
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class TransactionEntity {

    /** Client-supplied UUID — propagates as the cross-service correlation id. */
    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /** Amount in {@code currency}'s minor units, max 19 digits with 4 decimal places. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** ISO-4217 3-letter code; the DB-side CHECK enforces {@code ^[A-Z]{3}$}. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Optimistic-lock token — incremented on every UPDATE; protects against concurrent mutation. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
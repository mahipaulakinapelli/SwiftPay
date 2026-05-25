package com.swiftpay.ledger.entity;

import com.swiftpay.ledger.enums.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit log of state transitions on a {@link Transaction}.
 *
 * <p>Currently provisioned in the schema but not yet written by the ledger
 * (state transitions happen via direct UPDATE on the transaction row).
 * Reserved for richer audit/compliance reporting in a future phase.</p>
 *
 * <p>Note the deliberate absence of {@code @Version} — append-only tables
 * don't need optimistic locking; rows are never updated.</p>
 */
@Entity
@Table(name = "transaction_audit")
@Getter
@Setter
@NoArgsConstructor
public class TransactionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private TransactionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private TransactionStatus newStatus;

    /** Who/what triggered the transition — service name, user id, or similar. */
    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "reason", length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
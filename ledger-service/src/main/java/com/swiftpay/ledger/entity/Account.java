package com.swiftpay.ledger.entity;

import com.swiftpay.ledger.enums.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A single (user × currency) money-holding account.
 *
 * <p>The {@code UNIQUE(user_id, currency)} constraint enforces "one account
 * per user per currency". Auto-open of a receiver's account in a new
 * currency depends on this uniqueness for safety under concurrency.</p>
 *
 * <p>Optimistic-locked via {@link Version} — Hibernate appends
 * {@code WHERE version=?} on UPDATE; concurrent transfers on the same
 * account fail one of the writers cleanly.</p>
 */
@Entity
@Table(
    name = "accounts",
    uniqueConstraints = @UniqueConstraint(name = "uq_accounts_user_curr", columnNames = {"user_id", "currency"})
)
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner of the account; FK to {@code users.id} (enforced at the DB). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** ISO-4217 3-letter code; DB-side CHECK enforces uppercase. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Always non-negative — CHECK enforced at the DB. */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Optimistic-lock token. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
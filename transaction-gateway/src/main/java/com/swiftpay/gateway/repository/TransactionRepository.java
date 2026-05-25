package com.swiftpay.gateway.repository;

import com.swiftpay.gateway.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data repository for the gateway's {@code transactions} table.
 *
 * <p>Keyed by the client-supplied {@link UUID}. The unique-PK constraint
 * provides a hard idempotency floor on top of the Redis-based soft claim:
 * even if Redis were skipped or expired, a duplicate {@code save()} would
 * fail with a {@code DataIntegrityViolationException}.</p>
 */
@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {
}
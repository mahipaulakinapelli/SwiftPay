package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for the ledger's {@link OutboxEvent}.
 * See the gateway counterpart for the full contract — this is a symmetric copy.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(
            value = """
                    SELECT * FROM outbox_events
                    WHERE status IN ('PENDING','FAILED')
                      AND (
                          last_attempt_at IS NULL
                          OR last_attempt_at < NOW() - (LEAST(POWER(2, retry_count)::int, :maxBackoffSeconds) || ' seconds')::interval
                      )
                    ORDER BY created_at ASC
                    LIMIT :limit FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxEvent> claimPendingForPublish(@Param("limit") int limit,
                                             @Param("maxBackoffSeconds") int maxBackoffSeconds);

    @Modifying
    @Query(
            value = """
                    UPDATE outbox_events
                       SET status = 'PENDING'
                     WHERE status = 'PUBLISHING'
                       AND last_attempt_at < NOW() - (:thresholdSeconds || ' seconds')::interval
                    """,
            nativeQuery = true
    )
    int rescueStuckPublishing(@Param("thresholdSeconds") int thresholdSeconds);

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = com.swiftpay.ledger.outbox.OutboxStatus.PENDING")
    long countPending();

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = com.swiftpay.ledger.outbox.OutboxStatus.DEAD_LETTERED")
    long countDeadLettered();

    @Query("SELECT o FROM OutboxEvent o WHERE o.id = :id")
    OutboxEvent findOneById(@Param("id") UUID id);

    @Query(value = "SELECT NOW()", nativeQuery = true)
    OffsetDateTime now();
}
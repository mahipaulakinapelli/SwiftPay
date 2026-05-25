package com.swiftpay.gateway.repository;

import com.swiftpay.gateway.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link OutboxEvent}.
 *
 * <p>{@link #claimPendingForPublish} is the heart of the 2-phase publish:
 * native {@code FOR UPDATE SKIP LOCKED} hands a disjoint slice of PENDING
 * rows to each publisher instance, the same statement skips rows whose
 * exponential-backoff window hasn't elapsed yet, and the caller flips the
 * returned rows to {@code PUBLISHING} inside the same transaction so other
 * pollers/operators see they're in-flight.</p>
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Returns the oldest {@code PENDING} or {@code FAILED} rows whose backoff window has elapsed.
     * Must be invoked inside an active transaction; otherwise the row-level
     * locks are released immediately and the {@code SKIP LOCKED} guarantee
     * becomes meaningless. Backoff is {@code last_attempt_at + 2^retry_count seconds},
     * capped at {@code max_backoff_seconds}.
     */
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

    /**
     * Stuck-row rescue: any {@code PUBLISHING} row not updated in the last
     * {@code thresholdSeconds} is reset to {@code PENDING} (publisher likely crashed).
     */
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

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = com.swiftpay.gateway.outbox.OutboxStatus.PENDING")
    long countPending();

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = com.swiftpay.gateway.outbox.OutboxStatus.DEAD_LETTERED")
    long countDeadLettered();

    /** Test/ops helper for inspecting one row by id without going through the publisher. */
    @Query("SELECT o FROM OutboxEvent o WHERE o.id = :id")
    OutboxEvent findOneById(@Param("id") UUID id);

    /** Ops helper — current time on the DB side for backoff bookkeeping. */
    @Query(value = "SELECT NOW()", nativeQuery = true)
    OffsetDateTime now();
}
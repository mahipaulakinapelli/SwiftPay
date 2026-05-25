package com.swiftpay.analytics.repository;

import com.swiftpay.analytics.entity.AnalyticsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data repository for the analytics projection table.
 *
 * <p>{@link #existsByTransactionId} backs the consumer's idempotent insert;
 * {@link #getVolumeSummary} backs the {@code GET /analytics/volume} endpoint
 * via a record-typed JPQL projection.</p>
 */
@Repository
public interface AnalyticsTransactionRepository extends JpaRepository<AnalyticsTransaction, Long> {

    /** Pre-insert idempotency check — pairs with the UNIQUE constraint on the column. */
    boolean existsByTransactionId(UUID transactionId);

    /**
     * Aggregate row used by the volume endpoint. {@code COALESCE(SUM, 0)}
     * keeps the response a clean {@code 0} on an empty table.
     */
    @Query("""
            SELECT new com.swiftpay.analytics.repository.AnalyticsTransactionRepository$VolumeSummary(
                COUNT(t), COALESCE(SUM(t.amount), 0)
            )
            FROM AnalyticsTransaction t
            """)
    VolumeSummary getVolumeSummary();

    /**
     * Record-typed JPQL projection for {@link #getVolumeSummary}.
     *
     * @param totalTransactions row count of {@code analytics_transactions}
     * @param totalVolume       sum of {@code amount} across all rows (cross-currency)
     */
    record VolumeSummary(long totalTransactions, java.math.BigDecimal totalVolume) {}
}
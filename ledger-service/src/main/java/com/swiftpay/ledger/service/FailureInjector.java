package com.swiftpay.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only failure injector for the retry/DLT demo.
 *
 * <p>The {@code DebugController} arms a counter per {@code transactionId};
 * {@link LedgerProcessingService} calls {@link #consumeIfArmed} before any
 * state change and, while the counter is positive, throws
 * {@link com.swiftpay.ledger.exception.TransientLedgerException} — which
 * routes the message through the retry topic chain.</p>
 *
 * <p>Counters self-clear once exhausted. In production this bean would be
 * guarded behind a profile (e.g. {@code @ConditionalOnProperty("swiftpay.debug.enabled")}).</p>
 */
@Component
public class FailureInjector {

    private static final Logger log = LoggerFactory.getLogger(FailureInjector.class);

    private final ConcurrentMap<UUID, AtomicInteger> remainingFailures = new ConcurrentHashMap<>();

    /**
     * Arm the injector to fail the next {@code failures} processing attempts
     * of a specific transaction id.
     *
     * @param transactionId the id to target
     * @param failures      number of attempts to throw before letting the next one through
     */
    public void arm(UUID transactionId, int failures) {
        remainingFailures.put(transactionId, new AtomicInteger(failures));
        log.warn("FailureInjector armed: txId={} failures={}", transactionId, failures);
    }

    /**
     * If armed for {@code transactionId}, decrements the counter and returns
     * {@code true} — the caller should throw a transient exception. Returns
     * {@code false} (and clears the entry) once the counter is depleted.
     *
     * @param transactionId the id being processed
     * @return {@code true} if the caller should fake a transient failure
     */
    public boolean consumeIfArmed(UUID transactionId) {
        AtomicInteger counter = remainingFailures.get(transactionId);
        if (counter == null) {
            return false;
        }
        int remaining = counter.getAndDecrement();
        if (remaining <= 0) {
            remainingFailures.remove(transactionId);
            return false;
        }
        log.warn("FailureInjector trigger: txId={} remainingAfter={}", transactionId, remaining - 1);
        return true;
    }
}

package com.swiftpay.ledger.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FailureInjectorTest {

    @Test
    void consumeIfArmed_returnsFalse_whenNotArmed() {
        FailureInjector injector = new FailureInjector();
        assertThat(injector.consumeIfArmed(UUID.randomUUID())).isFalse();
    }

    @Test
    void consumeIfArmed_decrementsAndReleasesAfterCounterHitsZero() {
        FailureInjector injector = new FailureInjector();
        UUID id = UUID.randomUUID();
        injector.arm(id, 2);

        assertThat(injector.consumeIfArmed(id)).isTrue();
        assertThat(injector.consumeIfArmed(id)).isTrue();
        assertThat(injector.consumeIfArmed(id)).isFalse();
        // After exhaustion the counter is removed
        assertThat(injector.consumeIfArmed(id)).isFalse();
    }
}
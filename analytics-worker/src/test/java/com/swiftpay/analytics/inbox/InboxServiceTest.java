package com.swiftpay.analytics.inbox;

import com.swiftpay.analytics.entity.ProcessedEvent;
import com.swiftpay.analytics.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock ProcessedEventRepository repository;

    private InboxService service;

    @BeforeEach
    void setUp() {
        service = new InboxService(repository);
    }

    @Test
    void claim_firstSighting_savesProcessing() {
        UUID eventId = UUID.randomUUID();
        InboxEnvelope env = env(eventId);
        when(repository.findByEventIdAndConsumerGroup(eventId, "analytics-worker")).thenReturn(Optional.empty());

        service.claim(env);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProcessedEventStatus.PROCESSING);
    }

    @Test
    void claim_existingRow_throwsDuplicate() {
        UUID eventId = UUID.randomUUID();
        InboxEnvelope env = env(eventId);
        ProcessedEvent prior = new ProcessedEvent();
        prior.setStatus(ProcessedEventStatus.PROCESSED);
        when(repository.findByEventIdAndConsumerGroup(eventId, "analytics-worker")).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.claim(env)).isInstanceOf(DuplicateEventException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void claim_uniqueConstraintRace_throwsDuplicate() {
        UUID eventId = UUID.randomUUID();
        InboxEnvelope env = env(eventId);
        when(repository.findByEventIdAndConsumerGroup(eventId, "analytics-worker")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.claim(env)).isInstanceOf(DuplicateEventException.class);
    }

    @Test
    void markProcessed_updates() {
        UUID eventId = UUID.randomUUID();
        InboxEnvelope env = env(eventId);
        when(repository.updateStatus(eq(eventId), eq("analytics-worker"),
                eq(ProcessedEventStatus.PROCESSED), any(), eq(null))).thenReturn(1);

        service.markProcessed(env);

        verify(repository).updateStatus(eq(eventId), eq("analytics-worker"),
                eq(ProcessedEventStatus.PROCESSED), any(), eq(null));
    }

    private InboxEnvelope env(UUID eventId) {
        return new InboxEnvelope(
                eventId, "analytics-worker", "payment-completed",
                0, 1L, "com.swiftpay.analytics.event.PaymentCompletedEvent",
                UUID.randomUUID().toString(), null);
    }
}
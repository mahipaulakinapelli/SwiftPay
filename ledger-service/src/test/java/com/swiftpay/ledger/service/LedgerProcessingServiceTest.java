package com.swiftpay.ledger.service;

import com.swiftpay.ledger.enums.AccountStatus;
import com.swiftpay.ledger.enums.TransactionStatus;
import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.event.PaymentFailedEvent;
import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.cache.BalancesChangedEvent;
import com.swiftpay.ledger.config.TopicsProperties;
import com.swiftpay.ledger.entity.Account;
import com.swiftpay.ledger.entity.Transaction;
import com.swiftpay.ledger.exception.TransientLedgerException;
import com.swiftpay.ledger.inbox.DuplicateEventException;
import com.swiftpay.ledger.inbox.InboxEnvelope;
import com.swiftpay.ledger.inbox.InboxService;
import com.swiftpay.ledger.outbox.OutboxEventService;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerProcessingServiceTest {

    @Mock TransactionRepository txRepo;
    @Mock AccountRepository accountRepo;
    @Mock FailureInjector failureInjector;
    @Mock OutboxEventService outboxEventService;
    @Mock InboxService inboxService;
    @Mock ApplicationEventPublisher domainEventPublisher;

    private final TopicsProperties topics =
            new TopicsProperties("payment-initiated", "payment-completed", "payment-failed");

    LedgerProcessingService service;

    PaymentInitiatedEvent event;
    InboxEnvelope envelope;
    UUID txId;

    @BeforeEach
    void setUp() {
        service = new LedgerProcessingService(
                txRepo, accountRepo, failureInjector,
                outboxEventService, inboxService, domainEventPublisher, topics);
        txId = UUID.randomUUID();
        event = PaymentInitiatedEvent.of(
                txId, 1L, 2L, new BigDecimal("50.00"), "USD", TransactionStatus.PENDING);
        envelope = new InboxEnvelope(
                event.eventId(), "ledger-service", "payment-initiated",
                0, 42L, PaymentInitiatedEvent.class.getName(), txId.toString(), null);
    }

    @Test
    void successfulPayment_claimsInbox_recordsCompletedOutbox_publishesDomainEvent_marksProcessed() {
        Account sender = newAccount(10L, 1L, "USD", "1000.00");
        Account receiver = newAccount(20L, 2L, "USD", "200.00");
        when(accountRepo.findByUserIdAndCurrency(1L, "USD")).thenReturn(Optional.of(sender));
        when(accountRepo.findByUserIdAndCurrency(2L, "USD")).thenReturn(Optional.of(receiver));
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.process(envelope, event);

        verify(inboxService).claim(envelope);
        assertThat(sender.getBalance()).isEqualByComparingTo("950.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("250.00");

        ArgumentCaptor<Object> outboxPayload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventService, times(1)).record(
                eq("Payment"), eq(txId.toString()), eq("payment-completed"), outboxPayload.capture());
        PaymentCompletedEvent completed = (PaymentCompletedEvent) outboxPayload.getValue();
        assertThat(completed.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(completed.senderBalanceAfter()).isEqualByComparingTo("950.00");
        assertThat(completed.correlationId()).isEqualTo(event.correlationId());

        verify(inboxService).markProcessed(envelope);
        ArgumentCaptor<BalancesChangedEvent> domainCaptor = ArgumentCaptor.forClass(BalancesChangedEvent.class);
        verify(domainEventPublisher).publishEvent(domainCaptor.capture());
        assertThat(domainCaptor.getValue().senderId()).isEqualTo(1L);
        assertThat(domainCaptor.getValue().receiverId()).isEqualTo(2L);
    }

    @Test
    void duplicateRedelivery_inboxThrows_serviceShortCircuits_noWork() {
        doThrow(new DuplicateEventException("already seen"))
                .when(inboxService).claim(envelope);

        assertThatThrownBy(() -> service.process(envelope, event))
                .isInstanceOf(DuplicateEventException.class);

        verify(txRepo, never()).save(any());
        verify(outboxEventService, never()).record(any(), any(), any(), any());
        verify(domainEventPublisher, never()).publishEvent(any());
        verify(inboxService, never()).markProcessed(any());
    }

    @Test
    void insufficientFunds_marksFailedAndOutbox_doesNotPublishDomainEvent_butStillMarksInboxProcessed() {
        Account sender = newAccount(10L, 1L, "USD", "5.00");
        when(accountRepo.findByUserIdAndCurrency(1L, "USD")).thenReturn(Optional.of(sender));
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.process(envelope, event);

        ArgumentCaptor<Object> outboxPayload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventService, times(1)).record(
                eq("Payment"), eq(txId.toString()), eq("payment-failed"), outboxPayload.capture());
        PaymentFailedEvent failed = (PaymentFailedEvent) outboxPayload.getValue();
        assertThat(failed.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(failed.correlationId()).isEqualTo(event.correlationId());

        verify(accountRepo, never()).save(any());
        verify(domainEventPublisher, never()).publishEvent(any());
        verify(inboxService).markProcessed(envelope);
    }

    @Test
    void senderAccountMissing_marksFailed() {
        when(accountRepo.findByUserIdAndCurrency(1L, "USD")).thenReturn(Optional.empty());
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.process(envelope, event);

        ArgumentCaptor<Object> outboxPayload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventService).record(eq("Payment"), eq(txId.toString()), eq("payment-failed"), outboxPayload.capture());
        PaymentFailedEvent failed = (PaymentFailedEvent) outboxPayload.getValue();
        assertThat(failed.reasonCode()).isEqualTo("SENDER_ACCOUNT_NOT_FOUND");
        verify(domainEventPublisher, never()).publishEvent(any());
        verify(inboxService).markProcessed(envelope);
    }

    @Test
    void senderInactive_marksFailed() {
        Account sender = newAccount(10L, 1L, "USD", "1000.00");
        sender.setStatus(AccountStatus.FROZEN);
        when(accountRepo.findByUserIdAndCurrency(1L, "USD")).thenReturn(Optional.of(sender));
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.process(envelope, event);

        ArgumentCaptor<Object> outboxPayload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventService).record(eq("Payment"), eq(txId.toString()), eq("payment-failed"), outboxPayload.capture());
        PaymentFailedEvent failed = (PaymentFailedEvent) outboxPayload.getValue();
        assertThat(failed.reasonCode()).isEqualTo("SENDER_ACCOUNT_INACTIVE");
        verify(inboxService).markProcessed(envelope);
    }

    @Test
    void receiverAccountAutoCreated_whenMissing() {
        Account sender = newAccount(10L, 1L, "USD", "1000.00");
        when(accountRepo.findByUserIdAndCurrency(1L, "USD")).thenReturn(Optional.of(sender));
        when(accountRepo.findByUserIdAndCurrency(2L, "USD")).thenReturn(Optional.empty());
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.process(envelope, event);

        ArgumentCaptor<Account> capt = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo, times(2)).save(capt.capture());
        Account openedReceiver = capt.getAllValues().stream()
                .filter(a -> a.getUserId().equals(2L)).findFirst().orElseThrow();
        assertThat(openedReceiver.getCurrency()).isEqualTo("USD");
        assertThat(openedReceiver.getBalance()).isEqualByComparingTo("50.00");
        assertThat(openedReceiver.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void failureInjectorArmed_throwsTransientLedgerException_rollbackPath() {
        when(failureInjector.consumeIfArmed(txId)).thenReturn(true);

        assertThatThrownBy(() -> service.process(envelope, event))
                .isInstanceOf(TransientLedgerException.class)
                .hasMessageContaining(txId.toString());

        verify(inboxService).claim(envelope);
        verify(txRepo, never()).save(any());
        verify(accountRepo, never()).save(any());
        verify(outboxEventService, never()).record(any(), any(), any(), any());
        verify(domainEventPublisher, never()).publishEvent(any());
        // claim row goes away when the transaction rolls back — no markProcessed call
        verify(inboxService, never()).markProcessed(any());
    }

    private Account newAccount(long id, long userId, String currency, String balance) {
        Account a = new Account();
        a.setId(id);
        a.setUserId(userId);
        a.setCurrency(currency);
        a.setBalance(new BigDecimal(balance));
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private static OffsetDateTime now() { return OffsetDateTime.now(); }
}
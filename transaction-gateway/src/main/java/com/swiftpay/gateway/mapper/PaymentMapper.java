package com.swiftpay.gateway.mapper;

import com.swiftpay.gateway.enums.TransactionStatus;
import com.swiftpay.gateway.event.PaymentInitiatedEvent;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.entity.TransactionEntity;
import org.springframework.stereotype.Component;

/**
 * Hand-written conversions between gateway DTOs, the JPA entity, and the
 * Kafka event.
 *
 * <p>No MapStruct or reflection — explicit field assignments keep the mapper
 * trivially readable and statically verifiable. If a domain field is added,
 * the compiler points at every mapping that needs updating.</p>
 */
@Component
public class PaymentMapper {

    /**
     * Build a {@link TransactionEntity} ready for {@code save()}.
     *
     * @param request incoming HTTP DTO
     * @param status  initial transaction status to persist (typically {@link TransactionStatus#PENDING})
     */
    public TransactionEntity toEntity(PaymentRequest request, TransactionStatus status) {
        TransactionEntity entity = new TransactionEntity();
        entity.setTransactionId(request.transactionId());
        entity.setSenderId(request.senderId());
        entity.setReceiverId(request.receiverId());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setStatus(status);
        return entity;
    }

    /** Build the HTTP response DTO from a persisted entity. */
    public PaymentResponse toResponse(TransactionEntity entity) {
        return new PaymentResponse(
                entity.getTransactionId(),
                entity.getSenderId(),
                entity.getReceiverId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }

    /**
     * Build a {@code PaymentInitiatedEvent} from the persisted entity. The
     * factory stamps a fresh {@code eventId} and {@code occurredAt}.
     */
    public PaymentInitiatedEvent toEvent(TransactionEntity entity) {
        return PaymentInitiatedEvent.of(
                entity.getTransactionId(),
                entity.getSenderId(),
                entity.getReceiverId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus()
        );
    }
}
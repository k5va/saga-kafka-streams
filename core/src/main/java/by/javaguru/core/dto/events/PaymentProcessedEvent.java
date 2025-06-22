package by.javaguru.core.dto.events;

import java.util.UUID;

public class PaymentProcessedEvent extends SagaEvent {
    private UUID paymentId;

    public PaymentProcessedEvent() {
    }

    public PaymentProcessedEvent(UUID orderId, UUID paymentId) {
        super(orderId);
        this.paymentId = paymentId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }
}

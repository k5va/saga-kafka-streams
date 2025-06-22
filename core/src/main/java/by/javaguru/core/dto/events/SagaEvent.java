package by.javaguru.core.dto.events;

import java.util.UUID;

public class SagaEvent {
    private UUID orderId;

    public SagaEvent() {
    }

    public SagaEvent(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }


}

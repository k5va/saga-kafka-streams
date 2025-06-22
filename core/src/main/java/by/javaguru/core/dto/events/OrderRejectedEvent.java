package by.javaguru.core.dto.events;

import java.util.UUID;

public class OrderRejectedEvent extends SagaEvent {
    public OrderRejectedEvent() {
    }

    public OrderRejectedEvent(UUID orderId) {
        super(orderId);
    }
}

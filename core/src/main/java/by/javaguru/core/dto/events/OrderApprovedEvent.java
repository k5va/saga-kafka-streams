package by.javaguru.core.dto.events;

import java.util.UUID;

public class OrderApprovedEvent extends SagaEvent {
    public OrderApprovedEvent() {
    }

    public OrderApprovedEvent(UUID orderId) {
        super(orderId);
    }
}

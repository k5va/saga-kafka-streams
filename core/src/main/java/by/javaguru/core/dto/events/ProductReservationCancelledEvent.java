package by.javaguru.core.dto.events;

import java.util.UUID;

public class ProductReservationCancelledEvent extends SagaEvent {
    private UUID productId;

    public ProductReservationCancelledEvent() {
    }

    public ProductReservationCancelledEvent(UUID productId, UUID orderId) {
        super(orderId);
        this.productId = productId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }
}

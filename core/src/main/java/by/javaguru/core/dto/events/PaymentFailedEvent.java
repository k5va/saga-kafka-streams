package by.javaguru.core.dto.events;

import java.util.UUID;

public class PaymentFailedEvent extends SagaEvent {
    private UUID productId;
    private Integer productQuantity;

    public PaymentFailedEvent() {
    }

    public PaymentFailedEvent(UUID orderId, UUID productId, Integer productQuantity) {
        super(orderId);
        this.productId = productId;
        this.productQuantity = productQuantity;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public Integer getProductQuantity() {
        return productQuantity;
    }

    public void setProductQuantity(Integer productQuantity) {
        this.productQuantity = productQuantity;
    }
}

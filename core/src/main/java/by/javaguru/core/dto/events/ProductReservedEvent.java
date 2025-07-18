package by.javaguru.core.dto.events;

import java.math.BigDecimal;
import java.util.UUID;

public class ProductReservedEvent extends SagaEvent {
    private UUID productId;
    private BigDecimal productPrice;
    private Integer productQuantity;

    public ProductReservedEvent() {
    }

    public ProductReservedEvent(UUID orderId, UUID productId, BigDecimal productPrice, Integer productQuantity) {
        super(orderId);
        this.productId = productId;
        this.productPrice = productPrice;
        this.productQuantity = productQuantity;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public Integer getProductQuantity() {
        return productQuantity;
    }

    public void setProductQuantity(Integer productQuantity) {
        this.productQuantity = productQuantity;
    }
}

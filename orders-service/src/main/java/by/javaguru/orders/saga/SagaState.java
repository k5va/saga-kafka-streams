package by.javaguru.orders.saga;

import by.javaguru.core.types.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class SagaState {
    private UUID orderId;
    private UUID productId;
    private Integer productQuantity;
    private BigDecimal price;
    private OrderStatus status;
    private String errorMessage;

    public SagaState(UUID orderId, OrderStatus status, UUID productId, Integer productQuantity, BigDecimal price) {
        this.orderId = orderId;
        this.status = status;
        this.productId = productId;
        this.productQuantity = productQuantity;
        this.price = price;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}


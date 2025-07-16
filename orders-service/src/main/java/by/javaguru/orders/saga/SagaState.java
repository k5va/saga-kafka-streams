package by.javaguru.orders.saga;

import by.javaguru.core.types.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record SagaState(
    UUID orderId,
    UUID productId,
    Integer productQuantity,
    BigDecimal price,
    OrderStatus status,
    String errorMessage
) {
    public SagaState(UUID orderId, OrderStatus status, UUID productId, Integer productQuantity, BigDecimal price) {
        this(orderId, productId, productQuantity, price, status, null);
    }
}

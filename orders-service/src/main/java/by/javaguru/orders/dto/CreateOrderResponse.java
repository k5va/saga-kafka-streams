package by.javaguru.orders.dto;

import by.javaguru.core.types.OrderStatus;

import java.util.UUID;

public record CreateOrderResponse(
    UUID orderId,
    UUID customerId,
    UUID productId,
    Integer productQuantity,
    OrderStatus status
) {}

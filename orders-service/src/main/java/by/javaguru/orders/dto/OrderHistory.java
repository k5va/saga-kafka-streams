package by.javaguru.orders.dto;

import by.javaguru.core.types.OrderStatus;

import java.sql.Timestamp;
import java.util.UUID;

public record OrderHistory(
    UUID id,
    UUID orderId,
    OrderStatus status,
    Timestamp createdAt
) {}

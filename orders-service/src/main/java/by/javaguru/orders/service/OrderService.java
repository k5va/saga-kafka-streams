package by.javaguru.orders.service;

import by.javaguru.core.dto.Order;
import by.javaguru.orders.dto.CreateOrderRequest;
import by.javaguru.orders.dto.CreateOrderResponse;

import java.util.UUID;

public interface OrderService {
    CreateOrderResponse placeOrder(CreateOrderRequest order);
    void approveOrder(UUID orderId);
    void rejectOrder(UUID orderId);
}

package by.javaguru.orders.service;

import by.javaguru.core.types.OrderStatus;
import by.javaguru.orders.dto.OrderHistoryResponse;

import java.util.List;
import java.util.UUID;

public interface OrderHistoryService {
    void add(UUID orderId, OrderStatus orderStatus);

    List<OrderHistoryResponse> findByOrderId(UUID orderId);
}

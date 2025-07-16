package by.javaguru.orders.service;

import by.javaguru.orders.dto.OrderHistoryResponse;

import java.util.List;
import java.util.UUID;

public interface OrderHistoryService {
    List<OrderHistoryResponse> findByOrderId(UUID orderId);
}

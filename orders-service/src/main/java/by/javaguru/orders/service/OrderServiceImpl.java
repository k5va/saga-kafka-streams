package by.javaguru.orders.service;

import by.javaguru.core.dto.Order;
import by.javaguru.core.dto.events.OrderApprovedEvent;
import by.javaguru.core.dto.events.OrderCreatedEvent;
import by.javaguru.core.dto.events.OrderRejectedEvent;
import by.javaguru.core.types.OrderStatus;
import by.javaguru.orders.dao.jpa.entity.OrderEntity;
import by.javaguru.orders.dao.jpa.repository.OrderRepository;
import by.javaguru.orders.dto.CreateOrderRequest;
import by.javaguru.orders.dto.CreateOrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<UUID, Object> kafkaTemplate;
    private final String ordersEventsTopicName;

    public OrderServiceImpl(OrderRepository orderRepository,
                            KafkaTemplate<UUID, Object> kafkaTemplate,
                            @Value("${orders.events.topic.name}") String ordersEventsTopicName) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.ordersEventsTopicName = ordersEventsTopicName;
    }

    @Override
    public CreateOrderResponse placeOrder(CreateOrderRequest order) {
        OrderEntity entity = new OrderEntity();
        entity.setCustomerId(order.customerId());
        entity.setProductId(order.productId());
        entity.setProductQuantity(order.productQuantity());
        entity.setStatus(OrderStatus.CREATED);
        orderRepository.save(entity);

        OrderCreatedEvent placedOrder = new OrderCreatedEvent(
                entity.getId(),
                entity.getCustomerId(),
                order.productId(),
                order.productQuantity()
        );
        kafkaTemplate.send(ordersEventsTopicName, placedOrder.getOrderId(), placedOrder);

        return new CreateOrderResponse(
                entity.getId(),
                entity.getCustomerId(),
                entity.getProductId(),
                entity.getProductQuantity(),
                entity.getStatus()
        );
    }

    @Override
    public void approveOrder(UUID orderId) {
        OrderEntity orderEntity = orderRepository.findById(orderId).orElse(null);
        Assert.notNull(orderEntity, "No order is found with id " + orderId + " in the database table");
        orderEntity.setStatus(OrderStatus.APPROVED);
        orderRepository.save(orderEntity);
        OrderApprovedEvent orderApprovedEvent = new OrderApprovedEvent(orderId);
        kafkaTemplate.send(ordersEventsTopicName, orderId, orderApprovedEvent);
    }

    @Override
    public void rejectOrder(UUID orderId) {
        OrderEntity orderEntity = orderRepository.findById(orderId).orElse(null);
        Assert.notNull(orderEntity, "No order found with id: " + orderId);
        orderEntity.setStatus(OrderStatus.REJECTED);
        orderRepository.save(orderEntity);
        OrderRejectedEvent orderRejectedEvent = new OrderRejectedEvent(orderId);
        kafkaTemplate.send(ordersEventsTopicName, orderId, orderRejectedEvent);
    }

}

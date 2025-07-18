package by.javaguru.products.service.handler;

import by.javaguru.core.dto.Product;
import by.javaguru.core.dto.commands.CancelProductReservationCommand;
import by.javaguru.core.dto.events.ProductReservationCancelledEvent;
import by.javaguru.core.dto.commands.ReserveProductCommand;
import by.javaguru.core.dto.events.ProductReservationFailedEvent;
import by.javaguru.core.dto.events.ProductReservedEvent;
import by.javaguru.products.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@KafkaListener(topics = "${products.commands.topic.name}")
public class ProductCommandsHandler {

    private final ProductService productService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final KafkaTemplate<UUID, Object> kafkaTemplate;
    private final String productEventsTopicName;

    public ProductCommandsHandler(ProductService productService,
                                  KafkaTemplate<UUID, Object> kafkaTemplate,
                                  @Value("${products.events.topic.name}") String productEventsTopicName) {
        this.productService = productService;
        this.kafkaTemplate = kafkaTemplate;
        this.productEventsTopicName = productEventsTopicName;
    }

    @KafkaHandler
    public void handleCommand(@Payload ReserveProductCommand command) {

        try {
            Product desiredProduct = new Product(command.getProductId(), command.getProductQuantity());
            Product reservedProduct = productService.reserve(desiredProduct, command.getOrderId());
            ProductReservedEvent productReservedEvent = new ProductReservedEvent(command.getOrderId(),
                    command.getProductId(),
                    reservedProduct.getPrice(),
                    command.getProductQuantity());
            kafkaTemplate.send(productEventsTopicName, command.getOrderId(), productReservedEvent);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            ProductReservationFailedEvent productReservationFailedEvent =
                    new ProductReservationFailedEvent(command.getProductId(),
                            command.getOrderId(),
                            command.getProductQuantity());
            kafkaTemplate.send(productEventsTopicName, command.getOrderId(), productReservationFailedEvent);
        }
    }

    @KafkaHandler
    public void handleCommand(@Payload CancelProductReservationCommand command) {
        Product productToCancel = new Product(command.getProductId(), command.getProductQuantity());
        productService.cancelReservation(productToCancel, command.getOrderId());

        ProductReservationCancelledEvent productReservationCancelledEvent =
                new ProductReservationCancelledEvent(command.getProductId(), command.getOrderId());
        kafkaTemplate.send(productEventsTopicName, productReservationCancelledEvent.getOrderId(), productReservationCancelledEvent);
    }
}

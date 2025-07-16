package by.javaguru.orders.saga;

import by.javaguru.core.dto.events.OrderApprovedEvent;
import by.javaguru.core.dto.events.OrderCreatedEvent;
import by.javaguru.core.dto.events.OrderRejectedEvent;
import by.javaguru.core.dto.events.PaymentFailedEvent;
import by.javaguru.core.dto.events.PaymentProcessedEvent;
import by.javaguru.core.dto.events.ProductReservationCancelledEvent;
import by.javaguru.core.dto.events.ProductReservationFailedEvent;
import by.javaguru.core.dto.events.ProductReservedEvent;
import by.javaguru.core.dto.events.SagaEvent;
import by.javaguru.core.types.OrderStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@Slf4j
@Component
public class SagaEventsProcessor {
    public static final String SAGA_STATE_STORE = "order-saga-state-store";

    private KTable<UUID, SagaState> sagaStateTable;

    @Autowired
    void buildPipeline(StreamsBuilder streamsBuilder,
                       Serde<UUID> keySerde,
                       Serde<SagaEvent> sagaEventSerde,
                       Serde<SagaState> sagaStateSerde,
                       @Value("${orders.events.topic.name}") String orderEventsTopic,
                       @Value("${products.events.topic.name}") String productsEventsTopic,
                       @Value("${payments.events.topic.name}") String paymentsEventsTopic) {

        KStream<UUID, SagaEvent> sagaEvents = streamsBuilder.stream(
                Set.of(orderEventsTopic, productsEventsTopic, paymentsEventsTopic),
                Consumed.with(keySerde, sagaEventSerde)
        );

        this.sagaStateTable = sagaEvents
                .peek((uuid, sagaEvent) -> log.info("Received event {} for orderId: {}", sagaEvent, sagaEvent.getOrderId()))
                .groupByKey(Grouped.with(keySerde, sagaEventSerde))
                .aggregate(
                        () -> null, // initial state
                        (sagaId, event, currentState) -> {
                            if (currentState == null) {
                                return createNewSagaState((OrderCreatedEvent) event);
                            }

                            return switch (currentState.status()) {
                                case CREATED -> handleOrderCreatedEvent(currentState, event);
                                case PRODUCT_RESERVED -> handleProductReservedEvent(currentState, event);
                                case PAYMENT_PROCESSED -> handlePaymentProcessedEvent(currentState, event);
                                case PAYMENT_FAILED -> handlePaymentFailedEvent(currentState, event);
                                case PRODUCT_RESERVATION_CANCELLED, PRODUCT_RESERVATION_FAILED ->
                                        handleReservationFailedEvent(currentState, event);
                                case APPROVED, REJECTED -> currentState;
                            };
                        },
                        Materialized.<UUID, SagaState, KeyValueStore<Bytes, byte[]>>as(SAGA_STATE_STORE)
                                .withKeySerde(keySerde)
                                .withValueSerde(sagaStateSerde)
                );
    }

    private SagaState handleReservationFailedEvent(SagaState currentState, SagaEvent event) {
        if (event instanceof OrderRejectedEvent) {
            return updateSagaState(currentState, OrderStatus.REJECTED);
        }

        return currentState;
    }

    private SagaState handlePaymentFailedEvent(SagaState currentState, SagaEvent event) {
        if (event instanceof ProductReservationCancelledEvent) {
            return updateSagaState(currentState, OrderStatus.PRODUCT_RESERVATION_CANCELLED);
        }

        return currentState;
    }

    private SagaState handlePaymentProcessedEvent(SagaState currentState, SagaEvent event) {
        if (event instanceof OrderApprovedEvent) {
            return updateSagaState(currentState, OrderStatus.APPROVED);
        }

        return currentState;
    }

    private SagaState handleProductReservedEvent(SagaState currentState, SagaEvent event) {
        if (event instanceof PaymentProcessedEvent) {
            return updateSagaState(currentState, OrderStatus.PAYMENT_PROCESSED);
        } else if (event instanceof PaymentFailedEvent) {
            return updateSagaState(currentState,
                    OrderStatus.PAYMENT_FAILED,
                    "payment failed: " + event.getOrderId());
        }

        return currentState;
    }

    private SagaState handleOrderCreatedEvent(SagaState currentState, SagaEvent event) {
        if (event instanceof ProductReservedEvent) {
            return updateSagaState(currentState, OrderStatus.PRODUCT_RESERVED);
        } else if (event instanceof ProductReservationFailedEvent) {
            return updateSagaState(currentState,
                    OrderStatus.PRODUCT_RESERVATION_FAILED,
                    "product reservation failed: " + event.getOrderId());
        }

        return currentState;
    }

    private SagaState createNewSagaState(OrderCreatedEvent event) {
        return new SagaState(event.getOrderId(),
                OrderStatus.CREATED,
                event.getProductId(),
                event.getProductQuantity(),
                BigDecimal.TEN); //TODO: NEED real value
    }

    private SagaState updateSagaState(SagaState currentState, OrderStatus newStatus) {
        return new SagaState(
                currentState.orderId(),
                currentState.productId(),
                currentState.productQuantity(),
                currentState.price(),
                newStatus,
                currentState.errorMessage()
        );
    }

    private SagaState updateSagaState(SagaState currentState, OrderStatus newStatus, String errorMessage) {
        return new SagaState(
                currentState.orderId(),
                currentState.productId(),
                currentState.productQuantity(),
                currentState.price(),
                newStatus,
                errorMessage
        );
    }
}
package by.javaguru.orders.saga;

import by.javaguru.core.dto.commands.ApproveOrderCommand;
import by.javaguru.core.dto.commands.CancelProductReservationCommand;
import by.javaguru.core.dto.commands.ProcessPaymentCommand;
import by.javaguru.core.dto.commands.RejectOrderCommand;
import by.javaguru.core.dto.commands.ReserveProductCommand;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
@EnableKafkaStreams
@Slf4j
public class OrderSagaTopology {

    public static final String SAGA_STATE_STORE = "order-saga-state-store";


    @Bean
    public KTable<UUID, SagaState> sagaStateTable(StreamsBuilder streamsBuilder,
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

        KTable<UUID, SagaState> sagaStates = sagaEvents
                .peek((uuid, sagaEvent) -> log.info("Received event {} for orderId: {}",sagaEvent, sagaEvent.getOrderId()))
                .groupByKey(Grouped.with(keySerde, sagaEventSerde))
                .aggregate(
                        () -> null, // Initializer: null because the first event will set the state
                        (sagaId, event, currentState) -> {
                            if (event instanceof OrderCreatedEvent orderCreatedEvent) {
                                return new SagaState(orderCreatedEvent.getOrderId(),
                                        OrderStatus.CREATED,
                                        orderCreatedEvent.getProductId(),
                                        orderCreatedEvent.getProductQuantity(),
                                        BigDecimal.TEN); //TODO: NEED real value
                            } else if (currentState == null) {
                                log.warn("Received event for unknown saga: {}", event.getOrderId());
                                return null;
                            }

                            switch (currentState.status()) {
                                case CREATED:
                                    if (event instanceof ProductReservedEvent) {
                                        return updateSagaState(currentState, OrderStatus.PRODUCT_RESERVED);
                                    } else if (event instanceof ProductReservationFailedEvent) {
                                        return updateSagaState(currentState,
                                                OrderStatus.PRODUCT_RESERVATION_FAILED,
                                                "product reservation failed: " + event.getOrderId());
                                    }
                                    break;
                                case PRODUCT_RESERVED:
                                    if (event instanceof PaymentProcessedEvent) {
                                        return updateSagaState(currentState, OrderStatus.PAYMENT_PROCESSED);
                                    } else if (event instanceof PaymentFailedEvent) {
                                        return updateSagaState(currentState,
                                                OrderStatus.PAYMENT_FAILED,
                                                "payment failed: " + event.getOrderId());
                                    }
                                    break;
                                case PAYMENT_PROCESSED:
                                    if (event instanceof OrderApprovedEvent) {
                                        return updateSagaState(currentState, OrderStatus.APPROVED);
                                    }
                                    break;
                                case PAYMENT_FAILED:
                                    if (event instanceof ProductReservationCancelledEvent) {
                                        return updateSagaState(currentState, OrderStatus.PRODUCT_RESERVATION_CANCELLED);
                                    }
                                    break;
                                case PRODUCT_RESERVATION_CANCELLED:
                                case PRODUCT_RESERVATION_FAILED:
                                    if (event instanceof OrderRejectedEvent) {
                                        return updateSagaState(currentState, OrderStatus.REJECTED);
                                    }
                                    break;
                                case APPROVED:
                                case REJECTED:
                                    return currentState;
                                default:
                                    log.warn("Unhandled event {} for saga {} in status {}", event.getClass().getSimpleName(), sagaId, currentState.status());
                                    break;
                            }

                            return currentState; // Return current state if no transition
                        },
                        Materialized.<UUID, SagaState, KeyValueStore<Bytes, byte[]>>as(SAGA_STATE_STORE)
                                .withKeySerde(keySerde)
                                .withValueSerde(sagaStateSerde)
                );

        return sagaStates;
    }


    @Bean
    public KStream<UUID, Object> sagaCommandsStream(KTable<UUID, SagaState> sagaStateTable,
                                                    Serde<UUID> keySerde,
                                                    Serde<Object> sagaCommandSerde,
                                                    @Value("${orders.commands.topic.name}") String ordersCommandsTopic,
                                                    @Value("${products.commands.topic.name}") String productsCommandsTopic,
                                                    @Value("${payments.commands.topic.name}") String paymentsCommandsTopic) {

        KStream<UUID, Object> commandStream = sagaStateTable
                .toStream()
                .flatMapValues((sagaId, sagaState) -> {
                    List<Object> commandsToEmit = new ArrayList<>();

                    if (sagaState == null) {
                        return Collections.emptyList(); // Should be handled by aggregate, but good safeguard
                    }

                    switch (sagaState.status()) {
                        case CREATED:
                            commandsToEmit.add(new ReserveProductCommand(sagaState.productId(),
                                    sagaState.productQuantity(),
                                    sagaState.orderId()));
                            break;
                        case PRODUCT_RESERVED:
                            commandsToEmit.add(new ProcessPaymentCommand(sagaState.orderId(),
                                    sagaState.productId(),
                                    sagaState.price(),
                                    sagaState.productQuantity()));
                            break;
                        case PAYMENT_PROCESSED:
                            // Saga successfully completed
                            commandsToEmit.add(new ApproveOrderCommand(sagaState.orderId()));
                            break;
                        case PAYMENT_FAILED:
                            commandsToEmit.add(new CancelProductReservationCommand(sagaState.productId(),
                                    sagaState.orderId(),
                                    sagaState.productQuantity()));
                            break;
                        case PRODUCT_RESERVATION_FAILED:
                        case PRODUCT_RESERVATION_CANCELLED:
                            // Saga completed with failure
                            commandsToEmit.add(new RejectOrderCommand(sagaState.orderId()));
                            break;
                        default:
                            break;
                    }

                    // Return commands to be sent to respective topics
                    return commandsToEmit;
                })
                .peek((key, command) -> log.info("Emitting command: {} for saga {}", command.getClass().getSimpleName(), key));

        commandStream
                .filter((key, command) -> command instanceof ProcessPaymentCommand)
                .to(paymentsCommandsTopic, Produced.with(keySerde, sagaCommandSerde));

        commandStream
                .filter((key, command) ->
                        command instanceof ReserveProductCommand ||
                        command instanceof CancelProductReservationCommand)
                .to(productsCommandsTopic, Produced.with(keySerde, sagaCommandSerde));

        commandStream
                .filter((key, command) ->
                        command instanceof ApproveOrderCommand ||
                        command instanceof RejectOrderCommand)
                .to(ordersCommandsTopic, Produced.with(keySerde, sagaCommandSerde));

        return commandStream;
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

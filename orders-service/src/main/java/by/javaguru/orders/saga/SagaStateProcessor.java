package by.javaguru.orders.saga;

import by.javaguru.core.dto.commands.ApproveOrderCommand;
import by.javaguru.core.dto.commands.CancelProductReservationCommand;
import by.javaguru.core.dto.commands.ProcessPaymentCommand;
import by.javaguru.core.dto.commands.RejectOrderCommand;
import by.javaguru.core.dto.commands.ReserveProductCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class SagaStateProcessor {

    private final Serde<UUID> keySerde;
    private final Serde<Object> sagaCommandSerde;

    @Value("${orders.commands.topic.name}")
    private String ordersCommandsTopic;
    @Value("${products.commands.topic.name}")
    private String productsCommandsTopic;
    @Value("${payments.commands.topic.name}")
    private String paymentsCommandsTopic;

    @Autowired
    public void buildPipeline(SagaEventsProcessor sagaEventsProcessor) {
        KStream<UUID, Object> commandStream = sagaEventsProcessor
                .getSagaStateTable()
                .toStream()
                .mapValues((sagaId, sagaState) -> switch (sagaState.status()) {
                    case CREATED -> new ReserveProductCommand(sagaState.productId(),
                                sagaState.productQuantity(),
                                sagaState.orderId());
                    case PRODUCT_RESERVED -> new ProcessPaymentCommand(sagaState.orderId(),
                                sagaState.productId(),
                                sagaState.price(),
                                sagaState.productQuantity());
                    case PAYMENT_PROCESSED -> new ApproveOrderCommand(sagaState.orderId());
                    case PAYMENT_FAILED -> new CancelProductReservationCommand(sagaState.productId(),
                                sagaState.orderId(),
                                sagaState.productQuantity());
                    case PRODUCT_RESERVATION_FAILED,
                         PRODUCT_RESERVATION_CANCELLED -> new RejectOrderCommand(sagaState.orderId());
                    default -> null;
                })
                .peek((key, command) -> log.info("Emitting command: {} for saga {}",
                        command.getClass().getSimpleName(),
                        key)
                );

            sendCommands(commandStream);
    }

    private void sendCommands(KStream<UUID, Object> commandStream) {
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
    }
}

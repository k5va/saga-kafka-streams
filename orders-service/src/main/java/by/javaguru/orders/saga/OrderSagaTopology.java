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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
@EnableKafkaStreams
@Slf4j
public class OrderSagaTopology {

    public static final String SAGA_STATE_STORE = "order-saga-state-store";

    @Bean
    public Serde<SagaEvent> sagaEventSerde(ObjectMapper objectMapper) {
        return new JsonSerde<>(SagaEvent.class, objectMapper);
    }

    @Bean
    public Serde<Object> sagaCommandSerde(ObjectMapper objectMapper) {
        return new JsonSerde<>(Object.class, objectMapper);
    }

    @Bean
    public Serde<SagaState> sagaStateSerde(ObjectMapper objectMapper) {
        return new JsonSerde<>(SagaState.class, objectMapper);
    }

    @Bean
    public Serde<UUID> keySerde() {
        return Serdes.UUID();
    }

    @Bean
    public KafkaStreamsConfiguration defaultKafkaStreamsConfig(KafkaProperties kafkaProperties, OpenTelemetry openTelemetry) {

        var props = new HashMap<String, Object>(kafkaProperties.getStreams().getProperties());
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "orders-saga");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class);
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class);
        props.put(StreamsConfig.CONSUMER_PREFIX + ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                SagaTracingConsumerInterceptor.class.getName());
        props.put(StreamsConfig.PRODUCER_PREFIX + ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                SagaTracingProducerInterceptor.class.getName());
        props.put("saga.otel", openTelemetry);

        return new KafkaStreamsConfiguration(props);
    }

    public static class SagaTracingConsumerInterceptor implements ConsumerInterceptor<UUID, Object> {
        private static final String TRACER_SCOPE_NAME = "orders-saga";

        private TraceContextExtractor traceContextExtractor;

        @Override
        public ConsumerRecords<UUID, Object> onConsume(ConsumerRecords<UUID, Object> consumerRecords) {
            consumerRecords.forEach(record -> {
                Context parentContext = traceContextExtractor.extractTraceContext(record.headers());
                startAndEndSpan(parentContext, String.format("%s process", record.topic()));
            });

            return consumerRecords;
        }

        private void startAndEndSpan(Context parentContext, String spanName) {
            Span span = traceContextExtractor.getOpenTelemetry().getTracer(TRACER_SCOPE_NAME)
                    .spanBuilder(spanName)
                    .setParent(parentContext)
                    .setSpanKind(SpanKind.CONSUMER)
                    .startSpan();

            span.end();
        }

        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> map) {

        }

        @Override
        public void close() {

        }

        @Override
        public void configure(Map<String, ?> map) {
            this.traceContextExtractor = new TraceContextExtractor((OpenTelemetry) map.get("saga.otel"));
        }
    }

    public static class SagaTracingProducerInterceptor implements ProducerInterceptor<UUID, Object> {
        private static final String TRACER_SCOPE_NAME = "orders-saga";

        private TraceContextExtractor traceContextExtractor;

        @Override
        public ProducerRecord<UUID, Object> onSend(ProducerRecord<UUID, Object> record) {
            Context parentContext = traceContextExtractor.extractTraceContext(record.headers());
            log.info("Parent context {}", parentContext);

            startAndEndSpan(parentContext, String.format("%s publish", record.topic()));

            return record;
        }

        private void startAndEndSpan(Context parentContext, String spanName) {
            Span span = traceContextExtractor.getOpenTelemetry().getTracer(TRACER_SCOPE_NAME)
                    .spanBuilder(spanName)
                    .setParent(parentContext)
                    .setSpanKind(SpanKind.CONSUMER)
                    .startSpan();

            span.end();
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {

        }

        @Override
        public void close() {

        }

        @Override
        public void configure(Map<String, ?> configs) {
            this.traceContextExtractor = new TraceContextExtractor((OpenTelemetry) configs.get("saga.otel"));
        }
    }

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

                            switch (currentState.getStatus()) {
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
                                    log.warn("Unhandled event {} for saga {} in status {}", event.getClass().getSimpleName(), sagaId, currentState.getStatus());
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

    public static class TraceContextExtractor {
        @Getter
        private final OpenTelemetry openTelemetry;
        private final TextMapGetter<Headers> textMapGetter;

        public TraceContextExtractor(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
            this.textMapGetter = new KafkaHeadedersTextMapGetter();
        }

        public Context extractTraceContext(Headers headers) {
            return openTelemetry.getPropagators().getTextMapPropagator().extract(
                    Context.current(), headers, textMapGetter);
        }

        private static class KafkaHeadedersTextMapGetter implements TextMapGetter<Headers> {

            @Override
            public Iterable<String> keys(Headers headers) {
                return headers.toArray().length == 0 ?
                        Collections.emptyList() :
                        Arrays.stream(headers.toArray())
                                .map(Header::key)
                                .collect(Collectors.toList());
            }

            @Override
            public String get(Headers headers, String key) {
                Header header = headers.lastHeader(key);
                return header != null ? new String(header.value()) : null;
            }
        }

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

                    switch (sagaState.getStatus()) {
                        case CREATED:
                            commandsToEmit.add(new ReserveProductCommand(sagaState.getProductId(),
                                    sagaState.getProductQuantity(),
                                    sagaState.getOrderId()));
                            break;
                        case PRODUCT_RESERVED:
                            commandsToEmit.add(new ProcessPaymentCommand(sagaState.getOrderId(),
                                    sagaState.getProductId(),
                                    sagaState.getPrice(),
                                    sagaState.getProductQuantity()));
                            break;
                        case PAYMENT_PROCESSED:
                            // Saga successfully completed
                            commandsToEmit.add(new ApproveOrderCommand(sagaState.getOrderId()));
                            break;
                        case PAYMENT_FAILED:
                            commandsToEmit.add(new CancelProductReservationCommand(sagaState.getProductId(),
                                    sagaState.getOrderId(),
                                    sagaState.getProductQuantity()));
                            break;
                        case PRODUCT_RESERVATION_FAILED:
                        case PRODUCT_RESERVATION_CANCELLED:
                            // Saga completed with failure
                            commandsToEmit.add(new RejectOrderCommand(sagaState.getOrderId()));
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
        currentState.setStatus(newStatus);
        return currentState;
    }

    private SagaState updateSagaState(SagaState currentState, OrderStatus newStatus, String errorMessage) {
        currentState.setStatus(newStatus);
        currentState.setErrorMessage(errorMessage);
        return currentState;
    }
}

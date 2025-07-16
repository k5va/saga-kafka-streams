package by.javaguru.orders.config;

import by.javaguru.core.dto.events.SagaEvent;
import by.javaguru.orders.interceptors.SagaTracingConsumerInterceptor;
import by.javaguru.orders.interceptors.SagaTracingProducerInterceptor;
import by.javaguru.orders.saga.SagaState;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.util.HashMap;
import java.util.UUID;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    public static final String SAGA_OTEL = "saga.otel";

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
        props.put(SAGA_OTEL, openTelemetry);

        return new KafkaStreamsConfiguration(props);
    }

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
}

package by.javaguru.orders.interceptors;

import by.javaguru.orders.config.KafkaStreamsConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.Map;
import java.util.UUID;

@Slf4j
public class SagaTracingConsumerInterceptor implements ConsumerInterceptor<UUID, Object> {
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
        this.traceContextExtractor = new TraceContextExtractor((OpenTelemetry) map.get(KafkaStreamsConfig.SAGA_OTEL));
    }
}
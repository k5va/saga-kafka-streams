package by.javaguru.orders.interceptors;

import by.javaguru.orders.config.KafkaStreamsConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;
import java.util.UUID;

@Slf4j
public class SagaTracingProducerInterceptor implements ProducerInterceptor<UUID, Object> {
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
                .setSpanKind(SpanKind.PRODUCER)
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
        this.traceContextExtractor = new TraceContextExtractor((OpenTelemetry) configs.get(KafkaStreamsConfig.SAGA_OTEL));
    }
}
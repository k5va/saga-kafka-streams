package by.javaguru.orders.interceptors;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.Getter;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

public class TraceContextExtractor {
    @Getter
    private final OpenTelemetry openTelemetry;
    private final TextMapGetter<Headers> textMapGetter;

    public TraceContextExtractor(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.textMapGetter = new KafkaHeadersTextMapGetter();
    }

    public Context extractTraceContext(Headers headers) {
        return openTelemetry.getPropagators().getTextMapPropagator().extract(
                Context.current(), headers, textMapGetter);
    }

    private static class KafkaHeadersTextMapGetter implements TextMapGetter<Headers> {

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
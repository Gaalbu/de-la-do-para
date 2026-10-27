package br.com.deladopara.eventing.infrastructure;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Continues the producer's trace while a consumed record's effect runs (C79): the W3C {@code traceparent} header
 * written by the outbox publisher becomes the parent of an {@code event.consume} span. Without tracing it only runs the
 * work.
 */
public class ConsumerTracing {

    public static final ConsumerTracing NONE = new ConsumerTracing(null, null);

    private final Tracer tracer;
    private final Propagator propagator;

    public ConsumerTracing(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public <T> T inSpan(ConsumerRecord<String, String> record, String eventType, Supplier<T> work) {
        if (tracer == null || propagator == null) {
            return work.get();
        }
        var span = propagator
                .extract(record, ConsumerTracing::header)
                .name("event.consume")
                .tag("event.type", eventType)
                .tag("messaging.destination.name", record.topic())
                .start();
        try (var ignored = tracer.withSpan(span)) {
            return work.get();
        } catch (RuntimeException failure) {
            span.error(failure);
            throw failure;
        } finally {
            span.end();
        }
    }

    private static String header(ConsumerRecord<String, String> record, String key) {
        var header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.US_ASCII);
    }
}

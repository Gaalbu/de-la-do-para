package br.com.deladopara.eventing.adapter.persistence;

import br.com.deladopara.eventing.domain.OutboxEvent;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventWriter {

    private final OutboxEventRepository events;
    private final ObjectProvider<Tracer> tracer;

    public OutboxEventWriter(OutboxEventRepository events, ObjectProvider<Tracer> tracer) {
        this.events = events;
        this.tracer = tracer;
    }

    /** Stores the event with the current trace context, so the trace continues when it is published (C79). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboxEvent event) {
        events.save(new OutboxEventEntity(event, currentTraceParent()));
    }

    private String currentTraceParent() {
        var current = tracer.getIfAvailable();
        var span = current == null ? null : current.currentSpan();
        if (span == null) {
            return null;
        }
        var context = span.context();
        var sampled = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
        return "00-" + context.traceId() + "-" + context.spanId() + "-" + sampled;
    }
}

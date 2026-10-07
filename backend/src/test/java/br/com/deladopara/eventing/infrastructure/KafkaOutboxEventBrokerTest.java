package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.deladopara.eventing.domain.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KafkaOutboxEventBrokerTest {

    @Test
    void sendsAggregateAsKeyAndCanonicalEnvelopeAsValue() throws Exception {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = org.mockito.Mockito.mock(Producer.class);
        var metadata = new RecordMetadata(null, 0, 0, 0, 0, 0);
        when(producer.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(metadata));
        var broker = new KafkaOutboxEventBroker(producer, new ObjectMapper().findAndRegisterModules(), "events.v1");
        var event = event();

        broker.publish(event);

        var record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo("events.v1");
        assertThat(record.getValue().key()).isEqualTo("order-1");
        assertThat(record.getValue().value().toString()).contains("\"eventType\":\"order.created\"");
        assertThat(record.getValue().value().toString()).doesNotContain("attemptCount");
    }

    @Test
    void carriesTheStoredTraceContextAsAW3cHeader() throws Exception {
        @SuppressWarnings("unchecked")
        Producer<String, String> producer = org.mockito.Mockito.mock(Producer.class);
        when(producer.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new RecordMetadata(null, 0, 0, 0, 0, 0)));
        var broker = new KafkaOutboxEventBroker(producer, new ObjectMapper().findAndRegisterModules(), "events.v1");
        var traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        broker.publish(event(), traceParent);
        broker.publish(event(), null);

        var records = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer, org.mockito.Mockito.times(2)).send(records.capture());
        var traced = records.getAllValues().get(0).headers().lastHeader("traceparent");
        assertThat(new String(traced.value(), java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo(traceParent);
        assertThat(records.getAllValues().get(1).headers().lastHeader("traceparent"))
                .isNull();
    }

    private OutboxEvent event() {
        var now = Instant.parse("2026-09-22T12:00:00Z");
        var id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return new OutboxEvent(
                id,
                "order.created",
                1,
                "order-1",
                0,
                now,
                id,
                id,
                new ObjectMapper().createObjectNode().put("orderId", "order-1"));
    }
}

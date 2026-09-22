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

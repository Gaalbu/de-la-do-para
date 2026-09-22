package br.com.deladopara.eventing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UUID CORRELATION_ID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsVersionedEnvelopeAndProtectsPayloadCopy() {
        var payload = objectMapper.createObjectNode().put("orderId", "ord_01JABCDEF");

        var event = new OutboxEvent(
                EVENT_ID, "order.created", 1, "ord_01JABCDEF", 0, NOW, CORRELATION_ID, EVENT_ID, payload);

        payload.put("orderId", "changed-outside-event");

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.attemptCount()).isZero();
        assertThat(event.availableAt()).isEqualTo(NOW);
        assertThat(event.payload().get("orderId").asText()).isEqualTo("ord_01JABCDEF");
    }

    @Test
    void rejectsInvalidEnvelopeMetadata() {
        assertThatThrownBy(() -> new OutboxEvent(
                        EVENT_ID,
                        " ",
                        1,
                        "aggregate",
                        0,
                        NOW,
                        CORRELATION_ID,
                        EVENT_ID,
                        objectMapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event type is required");

        assertThatThrownBy(() -> new OutboxEvent(
                        EVENT_ID,
                        "order.created",
                        0,
                        "aggregate",
                        0,
                        NOW,
                        CORRELATION_ID,
                        EVENT_ID,
                        objectMapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Schema version must be positive");
    }
}

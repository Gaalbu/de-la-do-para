package br.com.deladopara.eventing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventEnvelopeValidatorTest {

    private final EventEnvelopeValidator validator = new EventEnvelopeValidator(new ObjectMapper());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsTheVersionedContractExample() throws Exception {
        var example = Files.readString(Path.of("../contracts/events/examples/order-created.valid.json"));
        var envelope = validator.validate(example);

        assertThat(envelope.eventType()).isEqualTo("pedido.criado");
        assertThat(envelope.schemaVersion()).isEqualTo(1);
    }

    @Test
    void rejectsTheInvalidContractExample() throws Exception {
        var example = Files.readString(Path.of("../contracts/events/examples/order-created.invalid.json"));

        assertThatThrownBy(() -> validator.validate(example)).isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    void rejectsAdditionalProperties() {
        var json = """
                {"eventId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","eventType":"pedido.criado",
                "schemaVersion":1,"aggregateId":"ord_1","aggregateVersion":0,
                "occurredAt":"2026-09-20T20:10:00Z","correlationId":"3fa85f64-5717-4562-b3fc-2c963f66afa6",
                "causationId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","payload":{},"unexpected":true}
                """;

        assertThatThrownBy(() -> validator.validate(json)).isInstanceOf(InvalidEventEnvelopeException.class);
    }
}

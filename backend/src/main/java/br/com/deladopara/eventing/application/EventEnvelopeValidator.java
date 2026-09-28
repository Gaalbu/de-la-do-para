package br.com.deladopara.eventing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class EventEnvelopeValidator {

    private final ObjectMapper objectMapper;
    private final Schema schema;

    public EventEnvelopeValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema();
    }

    public EventEnvelope validate(String json) {
        try {
            var node = objectMapper.readTree(json);
            var violations = schema.validate(json, InputFormat.JSON);
            if (!violations.isEmpty()) {
                throw new InvalidEventEnvelopeException("Event envelope does not match its JSON schema");
            }
            return objectMapper.treeToValue(node, EventEnvelope.class);
        } catch (IOException exception) {
            throw new InvalidEventEnvelopeException("Event envelope is not valid JSON", exception);
        }
    }

    public JsonNode validate(JsonNode node) {
        var violations = schema.validate(node.toString(), InputFormat.JSON);
        if (!violations.isEmpty()) {
            throw new InvalidEventEnvelopeException("Event envelope does not match its JSON schema");
        }
        return node;
    }

    private Schema loadSchema() {
        var resource = new ClassPathResource("contracts/events/envelope.schema.json");
        try (InputStream stream = resource.getInputStream()) {
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(stream, InputFormat.JSON);
        } catch (IOException exception) {
            throw new IllegalStateException("Event envelope JSON schema could not be loaded", exception);
        }
    }
}

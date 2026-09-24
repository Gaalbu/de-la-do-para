package br.com.deladopara.eventing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class EventEnvelopeValidator {

    private final ObjectMapper objectMapper;
    private final JsonSchema schema;

    public EventEnvelopeValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema(objectMapper);
    }

    public EventEnvelope validate(String json) {
        try {
            var node = objectMapper.readTree(json);
            var violations = schema.validate(node);
            if (!violations.isEmpty()) {
                throw new InvalidEventEnvelopeException("Event envelope does not match its JSON schema");
            }
            return objectMapper.treeToValue(node, EventEnvelope.class);
        } catch (IOException exception) {
            throw new InvalidEventEnvelopeException("Event envelope is not valid JSON", exception);
        }
    }

    public JsonNode validate(JsonNode node) {
        var violations = schema.validate(node);
        if (!violations.isEmpty()) {
            throw new InvalidEventEnvelopeException("Event envelope does not match its JSON schema");
        }
        return node;
    }

    private JsonSchema loadSchema(ObjectMapper mapper) {
        var resource = new ClassPathResource("contracts/events/envelope.schema.json");
        try (InputStream stream = resource.getInputStream()) {
            var schemaNode = mapper.readTree(stream);
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(schemaNode);
        } catch (IOException exception) {
            throw new IllegalStateException("Event envelope JSON schema could not be loaded", exception);
        }
    }
}

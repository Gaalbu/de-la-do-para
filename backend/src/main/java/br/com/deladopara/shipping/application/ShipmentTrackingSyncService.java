package br.com.deladopara.shipping.application;

import br.com.deladopara.shipping.adapter.MelhorEnvioLabelClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Queries one sandbox shipment and persists only its validated lifecycle status. */
@Service
@ConditionalOnProperty(prefix = "shipping.melhor-envio", name = "enabled", havingValue = "true")
public class ShipmentTrackingSyncService {

    private final MelhorEnvioLabelClient client;
    private final ShipmentTrackingService tracking;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ShipmentTrackingSyncService(
            MelhorEnvioLabelClient client, ShipmentTrackingService tracking, ObjectMapper objectMapper, Clock clock) {
        this.client = client;
        this.tracking = tracking;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public boolean synchronize(String providerShipmentId) {
        var response = client.tracking(providerShipmentId).body();
        var snapshot = snapshotFor(response, providerShipmentId);
        var status = snapshot.path("status");
        if (!status.isTextual()) {
            throw new ShippingAdapterException("shipping provider tracking response is invalid");
        }
        try {
            var bytes = objectMapper.writeValueAsBytes(snapshot);
            return tracking.recordProviderStatus(providerShipmentId, status.asText(), clock.instant(), bytes);
        } catch (IllegalArgumentException e) {
            throw new ShippingAdapterException("shipping provider tracking response is invalid");
        } catch (JsonProcessingException e) {
            throw new ShippingAdapterException("shipping provider tracking response could not be recorded");
        }
    }

    private static JsonNode snapshotFor(JsonNode response, String requestedId) {
        if (response == null || !response.isObject()) {
            throw new ShippingAdapterException("shipping provider tracking response is invalid");
        }
        var snapshot = response.path(requestedId);
        var responseId = snapshot.path("id");
        if (!snapshot.isObject() || !responseId.isTextual() || !requestedId.equals(responseId.asText())) {
            throw new ShippingAdapterException("shipping provider tracking response did not match the requested id");
        }
        return snapshot;
    }
}

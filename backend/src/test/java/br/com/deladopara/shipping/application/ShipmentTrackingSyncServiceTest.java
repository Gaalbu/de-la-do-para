package br.com.deladopara.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.deladopara.shipping.adapter.MelhorEnvioLabelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ShipmentTrackingSyncServiceTest {

    private static final String SHIPMENT_ID = "shipment-42";
    private static final Instant OBSERVED_AT = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void validatesTheMapKeyAndEmbeddedIdBeforePersistingAQuerySnapshot() throws Exception {
        var client = mock(MelhorEnvioLabelClient.class);
        var tracking = mock(ShipmentTrackingService.class);
        var mapper = new ObjectMapper();
        var snapshot = mapper.readTree("""
                {"shipment-42":{"id":"shipment-42","status":"posted","tracking":"ME123"}}
                """);
        when(client.tracking(SHIPMENT_ID)).thenReturn(new MelhorEnvioLabelClient.ProviderResponse(200, snapshot, null));
        var service =
                new ShipmentTrackingSyncService(client, tracking, mapper, Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

        assertThat(service.synchronize(SHIPMENT_ID)).isFalse();
        verify(tracking)
                .recordProviderStatus(
                        org.mockito.ArgumentMatchers.eq(SHIPMENT_ID),
                        org.mockito.ArgumentMatchers.eq("posted"),
                        org.mockito.ArgumentMatchers.eq(OBSERVED_AT),
                        org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void rejectsAResponseWhoseMapKeyOrEmbeddedIdDoesNotMatchTheRequest() throws Exception {
        var client = mock(MelhorEnvioLabelClient.class);
        var tracking = mock(ShipmentTrackingService.class);
        var mapper = new ObjectMapper();
        var response = mapper.readTree("""
                {"other-id":{"id":"other-id","status":"delivered"}}
                """);
        when(client.tracking(SHIPMENT_ID)).thenReturn(new MelhorEnvioLabelClient.ProviderResponse(200, response, null));
        var service =
                new ShipmentTrackingSyncService(client, tracking, mapper, Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.synchronize(SHIPMENT_ID))
                .isInstanceOf(ShippingAdapterException.class)
                .hasMessage("shipping provider tracking response did not match the requested id");
    }
}

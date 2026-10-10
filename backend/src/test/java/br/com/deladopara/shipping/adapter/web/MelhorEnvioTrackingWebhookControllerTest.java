package br.com.deladopara.shipping.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.deladopara.shipping.application.ShipmentTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class MelhorEnvioTrackingWebhookControllerTest {

    private static final String SECRET = "local-test-secret";
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-05T12:00:00Z");
    private final ShipmentTrackingService tracking = org.mockito.Mockito.mock(ShipmentTrackingService.class);
    private MelhorEnvioTrackingWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new MelhorEnvioTrackingWebhookController(tracking, new ObjectMapper(), SECRET);
    }

    @Test
    void acceptsAndPassesAlongAValidSignedEvent() throws Exception {
        var body = postedEvent();
        var response = controller.receive(signature(body), request(body));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(tracking).record("shipment-42", "order.posted", OCCURRED_AT, body);
    }

    @Test
    void rejectsAnInvalidSignatureBeforeCallingTheTrackingService() throws Exception {
        var response = controller.receive("invalid", request(postedEvent()));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(tracking);
    }

    @Test
    void rejectsBodiesOverTheConfiguredLimit() throws Exception {
        var body = new byte[MelhorEnvioTrackingWebhookController.MAX_BODY_BYTES + 1];
        var response = controller.receive(signature(body), request(body));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        verifyNoInteractions(tracking);
    }

    @Test
    void rejectsUnknownEventsEvenWhenTheyHaveAValidSignature() throws Exception {
        var body = "{\"event\":\"order.future\",\"data\":{\"id\":\"shipment-42\","
                .concat("\"created_at\":\"2026-10-05T12:00:00Z\"}}")
                .getBytes(StandardCharsets.UTF_8);
        var response = controller.receive(signature(body), request(body));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(tracking);
    }

    private static byte[] postedEvent() {
        return "{\"event\":\"order.posted\",\"data\":{\"id\":\"shipment-42\","
                .concat("\"posted_at\":\"2026-10-05T12:00:00Z\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static MockHttpServletRequest request(byte[] body) {
        var request = new MockHttpServletRequest();
        request.setContent(body);
        return request;
    }

    private static String signature(byte[] body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
}

package br.com.deladopara.shipping.adapter.web;

import br.com.deladopara.shipping.application.ShipmentTrackingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Accepts authenticated Melhor Envio webhook facts, with a bounded body and no raw-body persistence. */
@RestController
@RequestMapping("/api/v1/webhooks/melhor-envio")
public class MelhorEnvioTrackingWebhookController {

    static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Logger log = LoggerFactory.getLogger(MelhorEnvioTrackingWebhookController.class);
    private final ShipmentTrackingService tracking;
    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public MelhorEnvioTrackingWebhookController(
            ShipmentTrackingService tracking,
            ObjectMapper objectMapper,
            @Value("${shipping.melhor-envio.webhook-secret:}") String configuredSecret) {
        this.tracking = tracking;
        this.objectMapper = objectMapper;
        this.secret = configuredSecret == null || configuredSecret.isBlank()
                ? null
                : configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (secret == null) {
            log.warn("Melhor Envio webhook secret is not configured; tracking webhooks are rejected");
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "X-ME-Signature", required = false) String signature, HttpServletRequest request)
            throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        }
        var body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        }
        if (!validSignature(signature, body)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var notification = parse(body);
        if (notification == null) {
            return ResponseEntity.badRequest().build();
        }
        tracking.record(notification.shipmentId, notification.event, notification.occurredAt, body);
        return ResponseEntity.ok().build();
    }

    private boolean validSignature(String header, byte[] body) {
        if (secret == null || header == null || header.length() > 128) {
            return false;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            var supplied = Base64.getDecoder().decode(header);
            return MessageDigest.isEqual(mac.doFinal(body), supplied);
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    private Notification parse(byte[] body) {
        try {
            var root = objectMapper.readTree(body);
            var event = text(root, "event", 32);
            var data = root.path("data");
            var id = text(data, "id", 160);
            var timestamp = timestamp(data, event);
            if (event == null || id == null || timestamp == null) {
                return null;
            }
            // Validate the event before a database transaction starts.
            br.com.deladopara.shipping.domain.ShipmentTracking.from(event, timestamp);
            return new Notification(id, event, timestamp);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private static java.time.Instant timestamp(JsonNode data, String event) {
        if (event == null) {
            return null;
        }
        var field =
                switch (event) {
                    case "order.delivered" -> "delivered_at";
                    case "order.posted" -> "posted_at";
                    case "order.cancelled" -> "canceled_at";
                    default -> "created_at";
                };
        var node = data.path(field);
        if (!node.isTextual() || node.asText().length() > 40) {
            return null;
        }
        try {
            return java.time.Instant.parse(node.asText());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field, int maxLength) {
        var value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
            return null;
        }
        return value.asText();
    }

    private record Notification(String shipmentId, String event, java.time.Instant occurredAt) {}
}

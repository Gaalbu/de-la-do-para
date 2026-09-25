package br.com.deladopara.payments.adapter.web;

import br.com.deladopara.payments.application.ProviderEventInbox;
import br.com.deladopara.payments.application.ProviderEventInbox.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Asaas notifications: authenticates the shared token, stores the event and only then answers 2xx. A
 * database failure propagates as 5xx, so Asaas keeps the event and retries it.
 */
@RestController
@RequestMapping("/api/v1/webhooks/asaas")
public class AsaasWebhookController {

    static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookController.class);

    private final ProviderEventInbox inbox;
    private final ObjectMapper objectMapper;
    private final byte[] expectedToken;

    public AsaasWebhookController(
            ProviderEventInbox inbox,
            ObjectMapper objectMapper,
            @Value("${payments.asaas.webhook-token:}") String webhookToken) {
        this.inbox = inbox;
        this.objectMapper = objectMapper;
        this.expectedToken = valid(webhookToken) ? webhookToken.getBytes(StandardCharsets.UTF_8) : null;
        if (expectedToken == null) {
            log.warn(
                    "payments.asaas.webhook-token is not configured (32-255 chars, no spaces); Asaas webhooks are rejected");
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receive(
            @RequestHeader(name = "asaas-access-token", required = false) String token, @RequestBody byte[] body) {
        if (expectedToken == null
                || token == null
                || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            return problem(HttpStatus.UNAUTHORIZED, "PAYMENT_010", "token de webhook ausente ou inválido");
        }
        if (body.length > MAX_BODY_BYTES) {
            return problem(HttpStatus.CONTENT_TOO_LARGE, "PAYMENT_011", "corpo do webhook excede 64 KiB");
        }
        var notification = parse(body);
        if (notification == null) {
            return problem(HttpStatus.BAD_REQUEST, "PAYMENT_012", "payload de webhook inválido");
        }
        inbox.record("ASAAS", notification);
        return ResponseEntity.ok().build();
    }

    private Notification parse(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (java.io.IOException e) {
            return null;
        }
        var id = text(root, "id", 120);
        var event = text(root, "event", 40);
        if (id == null || event == null) {
            return null;
        }
        var checkout = root.path("checkout");
        var checkoutId = text(checkout, "id", 100);
        if (event.startsWith("CHECKOUT_") && checkoutId == null) {
            return null;
        }
        return new Notification(id, event, checkoutId, text(checkout, "status", 24), text(root, "dateCreated", 40));
    }

    private static String text(JsonNode node, String field, int maxLength) {
        var value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
            return null;
        }
        return value.asText();
    }

    private static boolean valid(String token) {
        return token != null
                && token.length() >= 32
                && token.length() <= 255
                && token.chars().noneMatch(Character::isWhitespace);
    }

    private static ResponseEntity<Problem> problem(HttpStatus status, String codigo, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(new Problem("Erro", status.value(), detail, codigo, MDC.get("correlationId")));
    }

    record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}

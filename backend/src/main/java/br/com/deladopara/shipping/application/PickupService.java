package br.com.deladopara.shipping.application;

import br.com.deladopara.orders.application.OrderFulfillmentPort;
import br.com.deladopara.orders.application.OrderFulfillmentPort.LockedOrder;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.shipping.adapter.PickupCodeVault;
import br.com.deladopara.shipping.adapter.persistence.PickupCodeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates local pickup state in shipping with order transitions through the orders application boundary. */
@Service
public class PickupService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final int RETENTION_BUSINESS_DAYS = 3;

    private final OrderFulfillmentPort orders;
    private final PickupCodeRepository codes;
    private final PickupCodeVault vault;
    private final Clock clock;

    public PickupService(OrderFulfillmentPort orders, PickupCodeRepository codes, PickupCodeVault vault, Clock clock) {
        this.orders = orders;
        this.codes = codes;
        this.vault = vault;
        this.clock = clock;
    }

    @Transactional
    public void startPreparation(UUID orderId, UUID correlationId) {
        var order = orders.lock(orderId);
        requirePickupState(order, OrderStatus.PAID);
        orders.transition(orderId, OrderStatus.PREPARING, OrderActor.ADMIN, null, correlationId);
    }

    @Transactional
    public void markReady(UUID orderId, UUID correlationId) {
        var order = orders.lock(orderId);
        requirePickupState(order, OrderStatus.PREPARING);
        if (codes.lock(orderId).isPresent()) {
            throw new PickupUnavailableException();
        }
        var code = generateCode();
        codes.insert(orderId, vault.encrypt(code), clock.instant());
        orders.transition(orderId, OrderStatus.READY_FOR_PICKUP, OrderActor.ADMIN, null, correlationId);
    }

    @Transactional
    public PickupInfo pickupInfo(UUID orderId) {
        var order = orders.lock(orderId);
        requirePickupState(order, OrderStatus.READY_FOR_PICKUP);
        var stored = codes.lock(orderId).orElseThrow(PickupUnavailableException::new);
        if (!"READY_FOR_PICKUP".equals(stored.status()) || stored.usedAt() != null || stored.ciphertext() == null) {
            throw new PickupUnavailableException();
        }
        var point = text(order, "label");
        var window = text(order, "window");
        return new PickupInfo(orderId, vault.decrypt(stored.ciphertext()), point, window, RETENTION_BUSINESS_DAYS);
    }

    @Transactional
    public void confirm(UUID orderId, String submittedCode, UUID correlationId) {
        if (submittedCode == null || !submittedCode.matches("[A-Z0-9]{10}")) {
            throw new InvalidPickupCodeException();
        }
        var order = orders.lock(orderId);
        requirePickupState(order, OrderStatus.READY_FOR_PICKUP);
        var stored = codes.lock(orderId).orElseThrow(InvalidPickupCodeException::new);
        if (!"READY_FOR_PICKUP".equals(stored.status())
                || stored.usedAt() != null
                || stored.ciphertext() == null
                || !MessageDigest.isEqual(
                        vault.decrypt(stored.ciphertext()).getBytes(StandardCharsets.US_ASCII),
                        submittedCode.getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidPickupCodeException();
        }
        orders.transition(orderId, OrderStatus.PICKED_UP, OrderActor.ADMIN, null, correlationId);
        codes.consume(orderId, clock.instant());
    }

    private static void requirePickupState(LockedOrder order, OrderStatus expected) {
        if (order.mode() != FulfillmentMode.PICKUP || order.status() != expected) {
            throw new PickupUnavailableException();
        }
    }

    private static String text(LockedOrder order, String field) {
        var value = order.destination().get(field);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new PickupUnavailableException();
    }

    private String generateCode() {
        var random = new SecureRandom();
        var code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    public record PickupInfo(UUID orderId, String code, String point, String window, int retentionBusinessDays) {}

    public static class PickupUnavailableException extends RuntimeException {}

    public static class InvalidPickupCodeException extends RuntimeException {}
}

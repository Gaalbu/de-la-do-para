package br.com.deladopara.payments.adapter.simulated;

import br.com.deladopara.payments.application.PaymentProvider;
import br.com.deladopara.payments.application.ProviderEvent;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Explicit simulated provider for local runs and journeys ({@code payments.provider=simulated}). It models the
 * behaviours the real adapter must survive: rejection before effect, timeout after effect and duplicated events.
 * Results from it are never evidence of Asaas behaviour.
 */
@Component
@ConditionalOnProperty(name = "payments.provider", havingValue = "simulated")
public class SimulatedPaymentProvider implements PaymentProvider {

    static final Duration LINK_VALIDITY = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<UUID, Checkout> byIntent = new ConcurrentHashMap<>();
    private final Map<String, UUID> intentByCheckout = new ConcurrentHashMap<>();
    private final List<ProviderEvent> delivered = new ArrayList<>();
    private final AtomicReference<Outcome> nextCreate = new AtomicReference<>(Outcome.CREATED);

    public SimulatedPaymentProvider(Clock clock) {
        this.clock = clock;
    }

    public enum Outcome {
        CREATED,
        REJECTED,
        TIMEOUT_AFTER_EFFECT,
        TIMEOUT_BEFORE_EFFECT
    }

    /** Applies to the next creation only; later creations go back to {@link Outcome#CREATED}. */
    public void failNextCreate(Outcome outcome) {
        nextCreate.set(outcome);
    }

    @Override
    public CreatedCheckout createCheckout(CheckoutRequest request) {
        var outcome = nextCreate.getAndSet(Outcome.CREATED);
        if (outcome == Outcome.REJECTED) {
            throw new ProviderRejectedException("HTTP_400");
        }
        if (outcome == Outcome.TIMEOUT_BEFORE_EFFECT) {
            throw new SimulatedTimeoutException();
        }
        var checkoutId = "sim_chk_" + UUID.randomUUID();
        var created = new CreatedCheckout(
                checkoutId,
                "https://simulado.local/checkout/" + checkoutId,
                clock.instant().plus(LINK_VALIDITY));
        byIntent.put(request.paymentIntentId(), new Checkout(created, CheckoutStatus.PENDING, request.amountCents()));
        intentByCheckout.put(checkoutId, request.paymentIntentId());
        if (outcome == Outcome.TIMEOUT_AFTER_EFFECT) {
            throw new SimulatedTimeoutException();
        }
        return created;
    }

    @Override
    public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
        return Optional.ofNullable(byIntent.get(paymentIntentId))
                .map(checkout ->
                        new CheckoutState(checkout.created().checkoutId(), checkout.status(), checkout.amountCents()));
    }

    /** Simulates the buyer paying on the hosted page; returns the notification the provider would send. */
    public ProviderEvent pay(String checkoutId, long paidAmountCents) {
        return settle(checkoutId, CheckoutStatus.PAID, ProviderEvent.Type.CHECKOUT_PAID, paidAmountCents);
    }

    public ProviderEvent expire(String checkoutId) {
        var amount = checkout(checkoutId).amountCents();
        return settle(checkoutId, CheckoutStatus.EXPIRED, ProviderEvent.Type.CHECKOUT_EXPIRED, amount);
    }

    /** At-least-once delivery: the same notification, same id, again. */
    public ProviderEvent redeliver(ProviderEvent event) {
        synchronized (delivered) {
            delivered.add(event);
        }
        return event;
    }

    public List<ProviderEvent> deliveredEvents() {
        synchronized (delivered) {
            return List.copyOf(delivered);
        }
    }

    private ProviderEvent settle(String checkoutId, CheckoutStatus status, ProviderEvent.Type type, long amount) {
        var intentId = intentByCheckout.get(checkoutId);
        var current = checkout(checkoutId);
        if (current.status() != CheckoutStatus.PENDING) {
            throw new IllegalStateException("Simulated checkout is already " + current.status());
        }
        byIntent.put(intentId, new Checkout(current.created(), status, current.amountCents()));
        var event = new ProviderEvent("sim_evt_" + UUID.randomUUID(), type, checkoutId, amount, clock.instant());
        synchronized (delivered) {
            delivered.add(event);
        }
        return event;
    }

    private Checkout checkout(String checkoutId) {
        var intentId = intentByCheckout.get(checkoutId);
        if (intentId == null) {
            throw new IllegalArgumentException("Unknown simulated checkout");
        }
        return byIntent.get(intentId);
    }

    private record Checkout(CreatedCheckout created, CheckoutStatus status, long amountCents) {}

    /** Stands in for a read timeout: the caller cannot tell whether the provider acted. */
    public static class SimulatedTimeoutException extends RuntimeException {

        public SimulatedTimeoutException() {
            super("simulated provider timeout");
        }
    }
}

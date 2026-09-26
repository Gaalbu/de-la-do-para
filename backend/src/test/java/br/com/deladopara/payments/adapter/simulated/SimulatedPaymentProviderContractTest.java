package br.com.deladopara.payments.adapter.simulated;

import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider.Outcome;
import br.com.deladopara.payments.application.PaymentProvider;
import br.com.deladopara.payments.application.PaymentProviderContract;
import br.com.deladopara.payments.application.ProviderEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class SimulatedPaymentProviderContractTest extends PaymentProviderContract {

    private final SimulatedPaymentProvider simulator =
            new SimulatedPaymentProvider(Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC));

    @Override
    protected PaymentProvider provider() {
        return simulator;
    }

    @Override
    protected void rejectNextCreation() {
        simulator.failNextCreate(Outcome.REJECTED);
    }

    @Override
    protected void timeOutAfterNextCreation() {
        simulator.failNextCreate(Outcome.TIMEOUT_AFTER_EFFECT);
    }

    @Override
    protected void timeOutBeforeNextCreation() {
        simulator.failNextCreate(Outcome.TIMEOUT_BEFORE_EFFECT);
    }

    @Override
    protected ProviderEvent pay(String checkoutId, long amountCents) {
        return simulator.pay(checkoutId, amountCents);
    }

    @Override
    protected ProviderEvent redeliver(ProviderEvent event) {
        return simulator.redeliver(event);
    }
}

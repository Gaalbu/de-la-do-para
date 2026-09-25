package br.com.deladopara.payments.infrastructure;

import br.com.deladopara.payments.application.CheckoutOperationRunner;
import br.com.deladopara.payments.application.CheckoutOperations;
import br.com.deladopara.payments.application.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives CREATE_CHECKOUT operations from the database: the durable claim, not a Kafka offset, decides who calls the
 * provider, so a redelivered {@code payment.checkout_requested} can never cause a second call. Enabled explicitly
 * with {@code payments.worker.enabled=true} on the worker profile.
 */
@Component
@Profile("worker")
@EnableScheduling
@ConditionalOnProperty(prefix = "payments.worker", name = "enabled", havingValue = "true")
public class PaymentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final CheckoutOperations operations;
    private final CheckoutOperationRunner runner;
    private final int batchSize;

    public PaymentWorker(
            CheckoutOperations operations,
            PaymentProvider provider,
            @Value("${payments.worker.batch-size:10}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Payment worker batch size must be positive");
        }
        this.operations = operations;
        this.runner = new CheckoutOperationRunner(operations, provider);
        this.batchSize = batchSize;
    }

    /** Returns how many operations ran; abandoned leases become UNKNOWN before new claims. */
    @Scheduled(fixedDelayString = "${payments.worker.poll-delay:PT1S}")
    public int tick() {
        var recovered = operations.recoverAbandoned();
        if (recovered > 0) {
            log.warn("payment operations with expired lease marked UNKNOWN: {}", recovered);
        }
        var ran = 0;
        while (ran < batchSize && runner.runNext()) {
            ran++;
        }
        return ran;
    }
}

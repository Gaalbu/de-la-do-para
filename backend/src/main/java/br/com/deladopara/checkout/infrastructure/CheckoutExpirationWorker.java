package br.com.deladopara.checkout.infrastructure;

import br.com.deladopara.checkout.application.CheckoutExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires due unpaid purchases on the worker profile. Enabled explicitly with {@code checkout.expiration.enabled=true};
 * several workers may run it, since each order is decided under its own lock.
 */
@Component
@Profile("worker")
@EnableScheduling
@ConditionalOnProperty(prefix = "checkout.expiration", name = "enabled", havingValue = "true")
public class CheckoutExpirationWorker {

    private static final Logger log = LoggerFactory.getLogger(CheckoutExpirationWorker.class);

    private final CheckoutExpirationService expirations;
    private final int batchSize;

    public CheckoutExpirationWorker(
            CheckoutExpirationService expirations, @Value("${checkout.expiration.batch-size:20}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Checkout expiration batch size must be positive");
        }
        this.expirations = expirations;
        this.batchSize = batchSize;
    }

    /** Returns how many orders this tick expired; one failing order does not hold back the others. */
    @Scheduled(fixedDelayString = "${checkout.expiration.poll-delay:PT5S}")
    public int tick() {
        var expired = 0;
        for (var orderId : expirations.dueOrders(batchSize)) {
            try {
                if (expirations.expire(orderId)) {
                    expired++;
                }
            } catch (RuntimeException failure) {
                log.warn("order {} could not be expired; it will be retried", orderId, failure);
            }
        }
        if (expired > 0) {
            log.info("expired unpaid orders: {}", expired);
        }
        return expired;
    }
}

package br.com.deladopara.payments.infrastructure;

import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.application.PaymentProvider;
import br.com.deladopara.payments.application.ProviderEventProcessor;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the notification processor the payment worker drives; same activation as {@link PaymentWorker}. */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
@ConditionalOnProperty(prefix = "payments.worker", name = "enabled", havingValue = "true")
class PaymentWorkerConfig {

    @Bean
    ProviderEventProcessor providerEventProcessor(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            PaymentIntentService intents,
            PaymentProvider provider,
            Clock clock) {
        return new ProviderEventProcessor(jdbc, tx, intents, provider, clock);
    }
}

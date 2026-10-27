package br.com.deladopara.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.application.PaymentProvider;
import br.com.deladopara.payments.application.ProviderEventProcessor;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** The payment worker needs a notification processor wherever it is enabled. */
class PaymentWorkerConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PaymentWorkerConfig.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class))
            .withBean(PaymentIntentService.class, () -> mock(PaymentIntentService.class))
            .withBean(PaymentProvider.class, () -> mock(PaymentProvider.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void enabledWorkerProfileProvidesTheProcessor() {
        context.withInitializer(app -> app.getEnvironment().setActiveProfiles("worker"))
                .withPropertyValues("payments.worker.enabled=true")
                .run(app -> assertThat(app).hasSingleBean(ProviderEventProcessor.class));
    }

    @Test
    void apiProcessDoesNotProcessNotifications() {
        context.withPropertyValues("payments.worker.enabled=true")
                .run(app -> assertThat(app).doesNotHaveBean(ProviderEventProcessor.class));
    }
}

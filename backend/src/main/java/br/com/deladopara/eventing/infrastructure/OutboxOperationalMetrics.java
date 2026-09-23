package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker")
public class OutboxOperationalMetrics {

    private final OutboxEventRepository events;
    private final Clock clock;

    @Autowired
    public OutboxOperationalMetrics(OutboxEventRepository events, MeterRegistry registry) {
        this(events, registry, Clock.systemUTC());
    }

    OutboxOperationalMetrics(OutboxEventRepository events, MeterRegistry registry, Clock clock) {
        this.events = events;
        this.clock = clock;
        Gauge.builder("dlp.eventing.outbox.pending.count", events, OutboxEventRepository::countPending)
                .description("Number of pending outbox events")
                .register(registry);
        Gauge.builder(
                        "dlp.eventing.outbox.pending.oldest_age_seconds",
                        this,
                        OutboxOperationalMetrics::oldestPendingAgeSeconds)
                .description("Age in seconds of the oldest pending outbox event")
                .register(registry);
        Gauge.builder("dlp.eventing.outbox.pending.attempts", events, OutboxEventRepository::sumPendingAttempts)
                .description("Sum of attempts for pending outbox events")
                .register(registry);
    }

    private double oldestPendingAgeSeconds() {
        Instant oldest = events.oldestPendingCreatedAt();
        if (oldest == null) {
            return 0;
        }
        return Math.max(0, Duration.between(oldest, clock.instant()).toMillis() / 1000.0);
    }
}

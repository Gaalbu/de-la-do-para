package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OutboxOperationalMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void exposesPendingCountAgeAndAttemptsWithoutEventLabels() {
        var events = mock(OutboxEventRepository.class);
        when(events.countPending()).thenReturn(3L);
        when(events.oldestPendingCreatedAt()).thenReturn(NOW.minusSeconds(90));
        when(events.sumPendingAttempts()).thenReturn(5L);
        var registry = new SimpleMeterRegistry();

        var metrics = new OutboxOperationalMetrics(events, registry, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(registry.get("dlp.eventing.outbox.pending.count").gauge().value())
                .isEqualTo(3.0);
        assertThat(registry.get("dlp.eventing.outbox.pending.oldest_age_seconds")
                        .gauge()
                        .value())
                .isEqualTo(90.0);
        assertThat(registry.get("dlp.eventing.outbox.pending.attempts").gauge().value())
                .isEqualTo(5.0);
        assertThat(registry.get("dlp.eventing.outbox.pending.count")
                        .meter()
                        .getId()
                        .getTags())
                .isEmpty();
        assertThat(metrics).isNotNull();
    }
}

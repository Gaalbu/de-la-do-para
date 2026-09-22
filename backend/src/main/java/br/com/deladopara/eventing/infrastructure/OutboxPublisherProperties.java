package br.com.deladopara.eventing.infrastructure;

import java.time.Duration;

public record OutboxPublisherProperties(Duration lease, int batchSize) {

    public OutboxPublisherProperties {
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Outbox lease must be positive");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Outbox batch size must be positive");
        }
    }
}

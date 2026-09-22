package br.com.deladopara.eventing.application;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventClaimService {

    private final OutboxEventRepository events;
    private final Clock clock;

    @Autowired
    public OutboxEventClaimService(OutboxEventRepository events) {
        this(events, Clock.systemUTC());
    }

    OutboxEventClaimService(OutboxEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claim(Instant now, Duration lease, int batchSize) {
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Outbox lease must be positive");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Outbox batch size must be positive");
        }

        var leaseUntil = now.plus(lease);
        var claimed = events.findClaimable(now, PageRequest.of(0, batchSize));
        claimed.forEach(event -> event.claim(leaseUntil));
        events.flush();
        return claimed.stream()
                .map(event -> new ClaimedOutboxEvent(event.toDomain(), leaseUntil))
                .toList();
    }

    @Transactional
    public List<ClaimedOutboxEvent> claim(Duration lease, int batchSize) {
        return claim(clock.instant(), lease, batchSize);
    }
}

package br.com.deladopara.eventing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.eventing.domain.OutboxEventStatus;
import br.com.deladopara.eventing.infrastructure.OutboxEventBroker;
import br.com.deladopara.eventing.infrastructure.OutboxPublishException;
import br.com.deladopara.eventing.infrastructure.OutboxPublisherProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private OutboxEventClaimService claimer;

    @Mock
    private OutboxEventRepository events;

    @Mock
    private OutboxEventBroker broker;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(
                claimer,
                events,
                broker,
                new OutboxPublisherProperties(Duration.ofSeconds(30), 10),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void marksPublishedOnlyAfterBrokerAck() {
        var event = event();
        var leaseUntil = NOW.plusSeconds(30);
        when(claimer.claim(NOW, Duration.ofSeconds(30), 10))
                .thenReturn(List.of(new ClaimedOutboxEvent(event, leaseUntil)));
        when(events.markPublished(EVENT_ID, leaseUntil, NOW)).thenReturn(1);

        assertThat(publisher.publishBatch()).isEqualTo(1);

        verify(broker).publish(event);
        verify(events).markPublished(EVENT_ID, leaseUntil, NOW);
    }

    @Test
    void leavesEventClaimedWhenBrokerFailsSoLeaseCanExpire() {
        var event = event();
        var leaseUntil = NOW.plusSeconds(30);
        when(claimer.claim(NOW, Duration.ofSeconds(30), 10))
                .thenReturn(List.of(new ClaimedOutboxEvent(event, leaseUntil)));
        doThrow(new OutboxPublishException("broker unavailable", new RuntimeException()))
                .when(broker)
                .publish(event);

        assertThat(publisher.publishBatch()).isZero();

        verify(events, never()).markPublished(EVENT_ID, leaseUntil, NOW);
    }

    @Test
    void doesNotCountAStaleClaimAsPublished() {
        var event = event();
        var leaseUntil = NOW.plusSeconds(30);
        when(claimer.claim(NOW, Duration.ofSeconds(30), 10))
                .thenReturn(List.of(new ClaimedOutboxEvent(event, leaseUntil)));
        when(events.markPublished(EVENT_ID, leaseUntil, NOW)).thenReturn(0);

        assertThat(publisher.publishBatch()).isZero();
    }

    @Test
    void serializesOnlyCanonicalEnvelopeFields() throws Exception {
        var envelope = EventEnvelope.from(event());
        var json = new ObjectMapper().findAndRegisterModules().writeValueAsString(envelope);

        assertThat(json).contains("\"eventId\"").contains("\"payload\"");
        assertThat(json).doesNotContain("attemptCount").doesNotContain("leaseUntil");
    }

    private OutboxEvent event() {
        var objectMapper = new ObjectMapper();
        return new OutboxEvent(
                EVENT_ID,
                "order.created",
                1,
                "ord_01JABCDEF",
                0,
                NOW,
                EVENT_ID,
                EVENT_ID,
                objectMapper.createObjectNode().put("orderId", "ord_01JABCDEF"),
                OutboxEventStatus.PENDING,
                NOW,
                1,
                NOW.plusSeconds(30),
                NOW);
    }
}

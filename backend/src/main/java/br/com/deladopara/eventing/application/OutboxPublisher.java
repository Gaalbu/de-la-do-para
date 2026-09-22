package br.com.deladopara.eventing.application;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import br.com.deladopara.eventing.infrastructure.OutboxEventBroker;
import br.com.deladopara.eventing.infrastructure.OutboxPublishException;
import br.com.deladopara.eventing.infrastructure.OutboxPublisherProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventClaimService claimer;
    private final OutboxEventRepository events;
    private final OutboxEventBroker broker;
    private final OutboxPublisherProperties properties;
    private final Clock clock;

    public OutboxPublisher(
            OutboxEventClaimService claimer,
            OutboxEventRepository events,
            OutboxEventBroker broker,
            OutboxPublisherProperties properties,
            Clock clock) {
        this.claimer = claimer;
        this.events = events;
        this.broker = broker;
        this.properties = properties;
        this.clock = clock;
    }

    public int publishBatch() {
        var now = clock.instant();
        var claimed = claimer.claim(now, properties.lease(), properties.batchSize());
        var published = 0;
        for (var item : claimed) {
            try {
                broker.publish(item.event());
                if (events.markPublished(item.event().eventId(), item.leaseUntil(), now) == 1) {
                    published++;
                } else {
                    LOGGER.warn(
                            "outbox_publish_claim_expired eventId={}",
                            item.event().eventId());
                }
            } catch (OutboxPublishException exception) {
                LOGGER.warn(
                        "outbox_publish_failed eventId={} reason=broker_ack_missing",
                        item.event().eventId());
            }
        }
        return published;
    }
}

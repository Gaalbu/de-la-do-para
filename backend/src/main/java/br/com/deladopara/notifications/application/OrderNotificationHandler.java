package br.com.deladopara.notifications.application;

import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.application.EventHandler;
import br.com.deladopara.notifications.adapter.persistence.OrderNotificationRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderNotificationHandler implements EventHandler {

    private final OrderNotificationRepository notifications;
    private final Clock clock;

    public OrderNotificationHandler(OrderNotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public String handlerName() {
        return "order-notifications";
    }

    @Override
    public String eventType() {
        return "order.created";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    @Transactional
    public void handle(EventEnvelope event) {
        notifications.enqueue(event, clock.instant());
    }

    @Component
    public static class StatusChanged implements EventHandler {

        private static final java.util.Set<String> STATUSES = java.util.Set.of(
                "PAID",
                "PREPARING",
                "READY_FOR_PICKUP",
                "IN_TRANSIT",
                "DELIVERED",
                "PICKED_UP",
                "CANCELLED",
                "EXPIRED",
                "UNDER_REVIEW");

        private final OrderNotificationRepository notifications;
        private final Clock clock;

        public StatusChanged(OrderNotificationRepository notifications, Clock clock) {
            this.notifications = notifications;
            this.clock = clock;
        }

        @Override
        public String handlerName() {
            return "order-notifications";
        }

        @Override
        public String eventType() {
            return "order.status_changed";
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        @Transactional
        public void handle(EventEnvelope event) {
            var status = event.payload().path("to").asText();
            if (STATUSES.contains(status)) {
                notifications.enqueue(event, clock.instant());
            }
        }
    }
}

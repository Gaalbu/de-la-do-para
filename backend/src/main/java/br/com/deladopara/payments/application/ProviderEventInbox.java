package br.com.deladopara.payments.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable inbox for provider notifications (SPEC-payments C60). Only identifiers and states are kept: the raw body
 * can carry customer data and is not stored. Processing happens later, after checking the provider (C61/C64).
 */
@Service
public class ProviderEventInbox {

    static final Set<String> HANDLED = Set.of("CHECKOUT_PAID", "CHECKOUT_CANCELED", "CHECKOUT_EXPIRED");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProviderEventInbox(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Returns false for a redelivery of an event already stored; both cases are safe to acknowledge. */
    @Transactional
    public boolean record(String provider, Notification notification) {
        var status = HANDLED.contains(notification.eventType()) ? "RECEIVED" : "IGNORED";
        return jdbc.update(
                        """
                        INSERT INTO payment_provider_event (provider, event_id, event_type, checkout_id, checkout_status,
                                                            provider_created, status, received_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (provider, event_id) DO NOTHING
                        """,
                        provider,
                        notification.eventId(),
                        notification.eventType(),
                        notification.checkoutId(),
                        notification.checkoutStatus(),
                        notification.providerCreated(),
                        status,
                        Timestamp.from(clock.instant()))
                == 1;
    }

    public record Notification(
            String eventId, String eventType, String checkoutId, String checkoutStatus, String providerCreated) {}
}

package br.com.deladopara.eventing.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.eventing.domain.OutboxEventStatus;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class OutboxEventPersistenceIT {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    private final OutboxEventRepository events;
    private final OutboxEventWriter writer;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    @Autowired
    OutboxEventPersistenceIT(
            OutboxEventRepository events,
            OutboxEventWriter writer,
            TransactionTemplate transactions,
            ObjectMapper objectMapper) {
        this.events = events;
        this.writer = writer;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    @Test
    void requiresAnExistingBusinessTransaction() {
        assertThatThrownBy(() -> writer.append(event())).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void persistsEnvelopeAndOperationalDefaultsWithinTheCallerTransaction() {
        var event = event();

        transactions.executeWithoutResult(status -> writer.append(event));

        var persisted = events.findById(event.eventId()).orElseThrow();
        assertThat(persisted.getEventType()).isEqualTo("order.created");
        assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(persisted.getAttemptCount()).isZero();
        assertThat(persisted.getAvailableAt()).isEqualTo(NOW);
        assertThat(persisted.getPayload().get("orderId").asText()).isEqualTo("ord_01JABCDEF");
    }

    @Test
    void rollsBackTheOutboxRowWhenTheCallerTransactionRollsBack() {
        var event = event();

        transactions.executeWithoutResult(status -> {
            writer.append(event);
            status.setRollbackOnly();
        });

        assertThat(events.findById(event.eventId())).isEmpty();
    }

    private OutboxEvent event() {
        var id = UUID.randomUUID();
        return new OutboxEvent(
                id,
                "order.created",
                1,
                "ord_01JABCDEF",
                0,
                NOW,
                id,
                id,
                objectMapper.createObjectNode().put("orderId", "ord_01JABCDEF"));
    }
}

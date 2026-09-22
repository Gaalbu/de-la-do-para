package br.com.deladopara.catalog.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.catalog.domain.Producer;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestContainer.class)
class ProducerRepositoryIT {

    private final ProducerRepository producers;

    @Autowired
    ProducerRepositoryIT(ProducerRepository producers) {
        this.producers = producers;
    }

    @Test
    @Transactional
    void savesLoadsAndUpdatesProducerWithoutChangingItsIdentityOrCreationTime() {
        var id = UUID.randomUUID();
        var createdAt = Instant.parse("2026-09-21T12:00:00Z");
        var updatedAt = Instant.parse("2026-09-21T13:00:00Z");
        var producer = new Producer(
                id,
                "DEMO-PRODUCER-" + id.toString().substring(0, 8),
                "Produtor de demonstração",
                "Origem fictícia — Belém, PA",
                "Registro fictício para demonstrar procedência.",
                createdAt);

        producers.save(producer);

        var loaded = producers.findBySlug(producer.getSlug()).orElseThrow();
        loaded.updateDetails(
                loaded.getSlug(),
                "Produtor atualizado",
                "Origem de demonstração atualizada",
                "Descrição atualizada.",
                updatedAt);
        producers.save(loaded);

        var persisted = producers.findById(id).orElseThrow();
        assertThat(producers.existsBySlug(persisted.getSlug())).isTrue();
        assertThat(persisted.getId()).isEqualTo(id);
        assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
        assertThat(persisted.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(persisted.getDisplayName()).isEqualTo("Produtor atualizado");
        assertThat(persisted.getOriginLabel()).isEqualTo("Origem de demonstração atualizada");
        assertThat(persisted.getDescription()).isEqualTo("Descrição atualizada.");
    }
}

package br.com.deladopara.catalog.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.support.PostgresTestContainer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class ProducerPersistenceIT {

    private final JdbcTemplate jdbc;

    @Autowired
    ProducerPersistenceIT(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void persistsOriginAndDescriptionAndKeepsIdentityWhenUpdated() {
        var id = UUID.randomUUID();
        var createdAt = Instant.parse("2026-09-21T12:00:00Z");
        var updatedAt = Instant.parse("2026-09-21T13:00:00Z");
        var slug = "demo-producer-" + id.toString().substring(0, 8);

        jdbc.update(
                "INSERT INTO producers (id, slug, display_name, origin_label, description, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                slug,
                "Produtor de demonstração",
                "Origem fictícia — Belém, PA",
                "Registro fictício para demonstrar procedência.",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));

        jdbc.update(
                "UPDATE producers SET display_name = ?, origin_label = ?, description = ?, updated_at = ? WHERE id = ?",
                "Produtor atualizado",
                "Origem de demonstração atualizada",
                "Descrição atualizada.",
                Timestamp.from(updatedAt),
                id);

        var persisted = jdbc.queryForMap("SELECT * FROM producers WHERE id = ?", id);

        assertThat(persisted.get("id")).isEqualTo(id);
        assertThat(persisted.get("slug")).isEqualTo(slug);
        assertThat(persisted.get("display_name")).isEqualTo("Produtor atualizado");
        assertThat(persisted.get("origin_label")).isEqualTo("Origem de demonstração atualizada");
        assertThat(persisted.get("description")).isEqualTo("Descrição atualizada.");
        assertThat(((Timestamp) persisted.get("created_at")).toInstant()).isEqualTo(createdAt);
        assertThat(((Timestamp) persisted.get("updated_at")).toInstant()).isEqualTo(updatedAt);
    }

    @Test
    void rejectsCaseInsensitiveDuplicateProducerSlugs() {
        var id = UUID.randomUUID();
        var now = Timestamp.from(Instant.parse("2026-09-21T12:00:00Z"));
        jdbc.update(
                "INSERT INTO producers (id, slug, display_name, origin_label, description, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                "belem-demo",
                "Produtor de demonstração",
                "Origem fictícia",
                "Registro fictício.",
                now,
                now);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO producers (id, slug, display_name, origin_label, description, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        "belem-demo",
                        "Outro produtor de demonstração",
                        "Outra origem fictícia",
                        "Outro registro fictício.",
                        now,
                        now))
                .isInstanceOf(DuplicateKeyException.class);
    }
}

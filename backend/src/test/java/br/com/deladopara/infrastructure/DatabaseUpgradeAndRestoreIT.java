package br.com.deladopara.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Migrations, atualização com dados e restauração isolada (C93). Cada cenário
 * usa um banco próprio dentro do mesmo PostgreSQL, então nenhum deles toca em
 * dados de outro nem do ambiente local.
 */
@Testcontainers
class DatabaseUpgradeAndRestoreIT {

    /** Primeira versão com a cadeia completa de compra que o seed preenche. */
    private static final String SNAPSHOT_VERSION = "30";

    private static final String SNAPSHOT_SEED = "db/snapshots/v30-seed.sql";

    private static final Map<String, String> SEEDED_KEYS = Map.of(
            "accounts", "id",
            "producers", "id",
            "products", "id",
            "product_skus", "id",
            "pricing_sku_prices", "sku_id",
            "inventory_lots", "id",
            "purchase_order", "id",
            "purchase_order_item", "order_id || ':' || line_no",
            "payment_intent", "id",
            "event_outbox", "event_id");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-bookworm");

    @Test
    void emptyDatabaseMigratesToTheLatestVersion() throws Exception {
        var database = createDatabase();
        var flyway = flyway(database);

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(flyway.info().all().length);
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(Integer.parseInt(flyway.info().current().getVersion().getVersion()))
                .isGreaterThan(Integer.parseInt(SNAPSHOT_VERSION));
        flyway.validate();
    }

    @Test
    void snapshotAtAnOlderVersionUpgradesWithoutLosingData() throws Exception {
        var database = snapshotDatabase();
        var keysBefore = readKeys(database);
        assertThat(keysBefore.values()).allSatisfy(keys -> assertThat(keys).isNotEmpty());

        var result = flyway(database).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);
        assertThat(readKeys(database)).isEqualTo(keysBefore);
        assertThat(scalar(database, "SELECT total_cents FROM purchase_order")).isEqualTo(6500);
        assertThat(scalar(database, "SELECT amount_cents FROM payment_intent")).isEqualTo(6500);
        assertThat(scalar(database, "SELECT sum(physical_units) FROM inventory_lots"))
                .isEqualTo(25);
        assertThat(scalar(database, "SELECT sum(reserved_units) FROM inventory_lots"))
                .isEqualTo(2);
    }

    @Test
    void restoreIntoASeparateDatabasePreservesDataConstraintsAndHistory() throws Exception {
        var source = snapshotDatabase();
        flyway(source).migrate();
        var sourceCounts = rowCounts(source);
        var dump = "/tmp/" + source + ".dump";

        run("pg_dump", "-U", POSTGRES.getUsername(), "-d", source, "-Fc", "-f", dump);
        var restored = createDatabase();
        run("pg_restore", "-U", POSTGRES.getUsername(), "-d", restored, "--no-owner", "--exit-on-error", dump);

        assertThat(rowCounts(restored)).isEqualTo(sourceCounts);
        assertThat(sourceCounts.get("purchase_order")).isEqualTo(1);
        assertThat(column(restored, constraintsQuery())).isEqualTo(column(source, constraintsQuery()));
        assertThat(column(restored, indexesQuery())).isEqualTo(column(source, indexesQuery()));
        assertThatThrownBy(() -> execute(restored, "UPDATE accounts SET role = 'ROOT'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("accounts_role_check");
        var flyway = flyway(restored);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(rowCounts(source)).isEqualTo(sourceCounts);
    }

    private static String snapshotDatabase() throws Exception {
        var database = createDatabase();
        configuration(database).target(SNAPSHOT_VERSION).load().migrate();
        try (var connection = connect(database)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(SNAPSHOT_SEED));
        }
        return database;
    }

    private static String createDatabase() throws SQLException {
        var name = "c93_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = connect(POSTGRES.getDatabaseName());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return name;
    }

    private static Flyway flyway(String database) {
        return configuration(database).load();
    }

    private static FluentConfiguration configuration(String database) {
        return Flyway.configure()
                .dataSource(url(database), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true);
    }

    private static Map<String, List<String>> readKeys(String database) throws SQLException {
        var keys = new LinkedHashMap<String, List<String>>();
        for (var table : SEEDED_KEYS.entrySet()) {
            keys.put(table.getKey(), column(database, "SELECT " + table.getValue() + " FROM " + table.getKey()));
        }
        return keys;
    }

    private static Map<String, Integer> rowCounts(String database) throws SQLException {
        var counts = new LinkedHashMap<String, Integer>();
        for (var table : column(
                database,
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
            counts.put(table, scalar(database, "SELECT count(*) FROM \"" + table + "\""));
        }
        return counts;
    }

    /**
     * O restore reescreve o texto de CHECKs com lista ({@code IN (...)}) sem mudar
     * o significado; por isso CHECKs são comparados por nome e provados por recusa.
     */
    private static String constraintsQuery() {
        return "SELECT conrelid::regclass::text || ' ' || conname || ' ' || contype::text || ' '"
                + " || CASE WHEN contype = 'c' THEN '' ELSE pg_get_constraintdef(oid) END"
                + " FROM pg_constraint WHERE connamespace = 'public'::regnamespace";
    }

    private static void execute(String database, String sql) throws SQLException {
        try (var connection = connect(database);
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String indexesQuery() {
        return "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public'";
    }

    private static int scalar(String database, String query) throws SQLException {
        return Integer.parseInt(column(database, query).getFirst());
    }

    private static List<String> column(String database, String query) throws SQLException {
        var values = new ArrayList<String>();
        try (var connection = connect(database);
                var statement = connection.createStatement();
                var results = statement.executeQuery(query)) {
            while (results.next()) {
                values.add(results.getString(1));
            }
        }
        values.sort(String::compareTo);
        return values;
    }

    private static void run(String... command) throws Exception {
        var result = POSTGRES.execInContainer(command);
        assertThat(result.getExitCode()).as("%s", result.getStderr()).isZero();
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(url(database), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String url(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }
}

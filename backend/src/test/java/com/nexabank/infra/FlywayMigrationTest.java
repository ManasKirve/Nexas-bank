package com.nexabank.infra;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 migration-hygiene contract (runs without any database):
 * versioned, ordered, non-destructive migrations covering every domain
 * table. Live migration runs are verified against PostgreSQL via Docker
 * (docs/database-migrations.md); this test guards the invariants in CI.
 */
class FlywayMigrationTest {

    private static final Path MIGRATIONS =
            Paths.get("src/main/resources/db/migration");

    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__.+\\.sql");

    @Test
    void migrationsExistInStrictVersionOrder() throws IOException {
        assertThat(MIGRATIONS).exists();
        List<String> files;
        try (Stream<Path> stream = Files.list(MIGRATIONS)) {
            files = stream.map(p -> p.getFileName().toString()).sorted().toList();
        }
        assertThat(files).isNotEmpty();
        List<Integer> versions = files.stream()
                .map(name -> {
                    var matcher = VERSIONED.matcher(name);
                    assertThat(matcher.matches())
                            .as("migration %s must match V<version>__<name>.sql", name)
                            .isTrue();
                    return Integer.parseInt(matcher.group(1));
                })
                .sorted()
                .toList();
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void migrationsAreNonDestructive() throws IOException {
        try (Stream<Path> stream = Files.list(MIGRATIONS)) {
            for (Path file : stream.sorted(Comparator.comparing(Path::toString)).toList()) {
                String sql = Files.readString(file).toUpperCase();
                assertThat(sql)
                        .as(file.getFileName().toString())
                        .doesNotContain("DROP TABLE")
                        .doesNotContain("DROP DATABASE")
                        .doesNotContain("TRUNCATE");
            }
        }
    }

    @Test
    void migrationsCoverEveryDomainTable() throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> stream = Files.list(MIGRATIONS)) {
            for (Path file : stream.sorted(Comparator.comparing(Path::toString)).toList()) {
                all.append(Files.readString(file).toUpperCase()).append('\n');
            }
        }
        String sql = all.toString();
        for (String table : List.of(
                "CUSTOMERS", "USERS", "ACCOUNTS", "TRANSACTIONS", "BENEFICIARIES",
                "FRAUD_EVALUATIONS", "FRAUD_ALERTS", "OUTBOX_EVENTS", "PROCESSED_EVENTS")) {
            assertThat(sql).contains(table);
        }
        // Transfer linkage columns arrive with the beneficiary migration.
        assertThat(sql).contains("TRANSFER_REFERENCE");
        assertThat(sql).contains("COUNTERPARTY_ACCOUNT_NUMBER");
    }
}

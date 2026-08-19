package com.gomech.api.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that validates every Flyway migration applies cleanly
 * to a fresh PostgreSQL 16 database (no Spring context required).
 *
 * <p>Runs via {@code maven-failsafe-plugin} (class name ends with {@code IT}).
 */
@Testcontainers
class FlywayMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gomech_test")
                    .withUsername("test")
                    .withPassword("test");

    private static MigrateResult migrateResult;

    @BeforeAll
    static void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        migrateResult = flyway.migrate();
    }

    @Test
    @DisplayName("All migrations apply without errors")
    void migrationsApplyCleanly() {
        assertTrue(migrateResult.success, "Flyway migrate should report success");
        assertTrue(migrateResult.migrationsExecuted > 0, "At least one migration should execute");
    }

    @Test
    @DisplayName("Expected baseline tables exist after migration")
    void expectedTablesExist() throws Exception {
        Set<String> expectedTables = Set.of(
                "tenants", "units", "users", "roles", "permissions",
                "role_permissions", "user_roles", "user_sessions",
                "customers", "vehicles",
                "suppliers", "products",
                "quotes", "quote_items", "work_orders", "appointments", "inspections", "inspection_items", "inventory_movements",
                "subscriptions", "payments", "financial_transactions",
                "audit_logs"
        );

        List<String> actualTables = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT table_name FROM information_schema.tables " +
                             "WHERE table_schema = 'public' " +
                             "AND table_type = 'BASE TABLE' " +
                             "AND table_name != 'flyway_schema_history'")) {

            while (rs.next()) {
                actualTables.add(rs.getString("table_name"));
            }
        }

        for (String table : expectedTables) {
            assertTrue(actualTables.contains(table),
                    "Expected table '" + table + "' not found. Actual tables: " + actualTables);
        }
    }

    @Test
    @DisplayName("UUID extension is enabled")
    void uuidExtensionEnabled() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM pg_extension WHERE extname = 'uuid-ossp'")) {

            assertTrue(rs.next(), "uuid-ossp extension should be installed");
        }
    }

    @Test
    @DisplayName("Primary keys use UUID type")
    void primaryKeysAreUuid() throws Exception {
        // Spot-check a few core tables
        String[] tables = {"tenants", "users", "work_orders", "audit_logs"};

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {

            for (String table : tables) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT data_type FROM information_schema.columns " +
                                "WHERE table_schema = 'public' " +
                                "AND table_name = '" + table + "' " +
                                "AND column_name = 'id'")) {

                    assertTrue(rs.next(), "Table " + table + " should have an 'id' column");
                    assertEquals("uuid", rs.getString("data_type"),
                            "PK of " + table + " should be UUID");
                }
            }
        }
    }

    @Test
    @DisplayName("Timestamp columns use TIMESTAMP WITH TIME ZONE")
    void timestampColumnsAreTimestamptz() throws Exception {
        // Spot-check created_at on a few tables
        String[] tables = {"tenants", "users", "audit_logs"};

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {

            for (String table : tables) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT data_type FROM information_schema.columns " +
                                "WHERE table_schema = 'public' " +
                                "AND table_name = '" + table + "' " +
                                "AND column_name = 'created_at'")) {

                    assertTrue(rs.next(), table + " should have a 'created_at' column");
                    assertEquals("timestamp with time zone", rs.getString("data_type"),
                            "created_at on " + table + " should be TIMESTAMP WITH TIME ZONE");
                }
            }
        }
    }

    @Test
    @DisplayName("Mutable tables have version column for optimistic locking")
    void mutableTablesHaveVersionColumn() throws Exception {
        String[] mutableTables = {"tenants", "users", "work_orders", "products", "quotes"};

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {

            for (String table : mutableTables) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT data_type FROM information_schema.columns " +
                                "WHERE table_schema = 'public' " +
                                "AND table_name = '" + table + "' " +
                                "AND column_name = 'version'")) {

                    assertTrue(rs.next(), table + " should have a 'version' column");
                    assertEquals("bigint", rs.getString("data_type"),
                            "version on " + table + " should be BIGINT");
                }
            }
        }
    }

    @Test
    @DisplayName("Row Level Security is enabled on tenant and unit tables")
    void rowLevelSecurityIsEnabledOnTenantTables() throws Exception {
        String[] rlsTables = {"tenants", "units", "users", "work_orders", "quotes", "quote_items", "customers", "appointments", "inspections", "inspection_items", "inventory_movements"};

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {

            for (String table : rlsTables) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT rowsecurity FROM pg_tables " +
                                "WHERE schemaname = 'public' AND tablename = '" + table + "'")) {

                    assertTrue(rs.next(), "Table " + table + " should be in pg_tables");
                    assertTrue(rs.getBoolean("rowsecurity"),
                            "Row Level Security should be enabled on " + table);
                }
            }
        }
    }

    @Test
    @DisplayName("Flyway schema history is consistent with migration files")
    void flywayHistoryIsConsistent() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrationInfo[] applied = flyway.info().applied();
        assertNotNull(applied);
        assertTrue(applied.length >= 3, "At least 3 migrations (V1, V2, and V3) should be in history");

        for (MigrationInfo info : applied) {
            assertNotNull(info.getVersion(), "Applied migration should have a version");
            assertNull(info.getState().isResolved() ? null : info.getState(),
                    "Migration " + info.getVersion() + " should be in resolved state");
        }
    }
}

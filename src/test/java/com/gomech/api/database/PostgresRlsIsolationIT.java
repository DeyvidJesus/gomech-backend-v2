package com.gomech.api.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test validating PostgreSQL Row-Level Security (RLS) for Tenant and Unit Isolation.
 *
 * <p>Exercises a non-superuser database role ({@code rls_test_app_user}) against V3 Flyway RLS policies:
 * <ol>
 *   <li>Tenant-level isolation: queries return only rows for {@code app.current_tenant}.</li>
 *   <li>Unit-level isolation: queries with {@code app.current_unit} return only that branch's rows.</li>
 *   <li>Global tenant visibility: queries with unset {@code app.current_unit} access all units in the tenant.</li>
 *   <li>Fail-closed default: queries without {@code app.current_tenant} return 0 rows.</li>
 *   <li>Cross-tenant and cross-unit write denial: unauthorized inserts violate RLS {@code WITH CHECK} policy.</li>
 *   <li>Session boundary reset: {@code SET LOCAL} resets cleanly on transaction completion.</li>
 * </ol>
 */
@Testcontainers
class PostgresRlsIsolationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gomech_rls_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final String APP_USER = "rls_test_app_user";
    private static final String APP_PASS = "rls_secret_123";

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    private static final UUID UNIT_A1 = UUID.randomUUID(); // Matriz Tenant A
    private static final UUID UNIT_A2 = UUID.randomUUID(); // Filial Tenant A
    private static final UUID UNIT_B1 = UUID.randomUUID(); // Matriz Tenant B

    private static final UUID CUST_A = UUID.randomUUID();
    private static final UUID VEH_A = UUID.randomUUID();

    private static final UUID CUST_B = UUID.randomUUID();
    private static final UUID VEH_B = UUID.randomUUID();

    private static final UUID WO_A1 = UUID.randomUUID();
    private static final UUID WO_A2 = UUID.randomUUID();
    private static final UUID WO_B1 = UUID.randomUUID();

    @BeforeAll
    static void setupDatabaseAndRls() throws Exception {
        // 1. Run standard Flyway migrations (V1, V2, V3 RLS) as admin/superuser
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // 2. Seed test data and create restricted non-superuser role
        try (Connection adminConn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = adminConn.createStatement()) {

            // Create tenants
            stmt.execute("INSERT INTO tenants (id, name, cnpj) VALUES ('" + TENANT_A + "', 'Workshop A Corp', '11.111.111/0001-11');");
            stmt.execute("INSERT INTO tenants (id, name, cnpj) VALUES ('" + TENANT_B + "', 'Workshop B Corp', '22.222.222/0001-22');");

            // Create units
            stmt.execute("INSERT INTO units (id, tenant_id, name, is_headquarters) VALUES ('" + UNIT_A1 + "', '" + TENANT_A + "', 'A - Matriz', true);");
            stmt.execute("INSERT INTO units (id, tenant_id, name, is_headquarters) VALUES ('" + UNIT_A2 + "', '" + TENANT_A + "', 'A - Filial', false);");
            stmt.execute("INSERT INTO units (id, tenant_id, name, is_headquarters) VALUES ('" + UNIT_B1 + "', '" + TENANT_B + "', 'B - Matriz', true);");

            // Seed users
            stmt.execute("INSERT INTO users (id, tenant_id, name, email, password_hash) " +
                    "VALUES ('" + UUID.randomUUID() + "', '" + TENANT_A + "', 'Alice A', 'alice@tenant-a.com', 'pwd');");
            stmt.execute("INSERT INTO users (id, tenant_id, name, email, password_hash) " +
                    "VALUES ('" + UUID.randomUUID() + "', '" + TENANT_A + "', 'Adam A', 'adam@tenant-a.com', 'pwd');");
            stmt.execute("INSERT INTO users (id, tenant_id, name, email, password_hash) " +
                    "VALUES ('" + UUID.randomUUID() + "', '" + TENANT_B + "', 'Bob B', 'bob@tenant-b.com', 'pwd');");

            // Seed customers and vehicles
            stmt.execute("INSERT INTO customers (id, tenant_id, name) VALUES ('" + CUST_A + "', '" + TENANT_A + "', 'Customer A');");
            stmt.execute("INSERT INTO vehicles (id, tenant_id, customer_id, license_plate) VALUES ('" + VEH_A + "', '" + TENANT_A + "', '" + CUST_A + "', 'ABC-1234');");

            stmt.execute("INSERT INTO customers (id, tenant_id, name) VALUES ('" + CUST_B + "', '" + TENANT_B + "', 'Customer B');");
            stmt.execute("INSERT INTO vehicles (id, tenant_id, customer_id, license_plate) VALUES ('" + VEH_B + "', '" + TENANT_B + "', '" + CUST_B + "', 'XYZ-9876');");

            // Seed work orders
            stmt.execute("INSERT INTO work_orders (id, tenant_id, unit_id, vehicle_id, total_amount, status) " +
                    "VALUES ('" + WO_A1 + "', '" + TENANT_A + "', '" + UNIT_A1 + "', '" + VEH_A + "', 500.00, 'PLANNED');");
            stmt.execute("INSERT INTO work_orders (id, tenant_id, unit_id, vehicle_id, total_amount, status) " +
                    "VALUES ('" + WO_A2 + "', '" + TENANT_A + "', '" + UNIT_A2 + "', '" + VEH_A + "', 750.00, 'IN_PROGRESS');");
            stmt.execute("INSERT INTO work_orders (id, tenant_id, unit_id, vehicle_id, total_amount, status) " +
                    "VALUES ('" + WO_B1 + "', '" + TENANT_B + "', '" + UNIT_B1 + "', '" + VEH_B + "', 1200.00, 'COMPLETED');");

            // Create non-superuser role and grant permissions
            stmt.execute("DO $$ BEGIN " +
                    "IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '" + APP_USER + "') THEN " +
                    "CREATE ROLE " + APP_USER + " WITH LOGIN PASSWORD '" + APP_PASS + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; " +
                    "END IF; END $$;");

            stmt.execute("GRANT CONNECT ON DATABASE gomech_rls_test TO " + APP_USER + ";");
            stmt.execute("GRANT USAGE ON SCHEMA public TO " + APP_USER + ";");
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + APP_USER + ";");
        }
    }

    private Connection getAppUserConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASS);
    }

    @Test
    @DisplayName("Tenant isolation: queries return only rows belonging to the active tenant")
    void rlsFiltersRowsByTenantSessionVariable() throws Exception {
        try (Connection conn = getAppUserConnection()) {
            conn.setAutoCommit(false);

            // Set session variable for Tenant A
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "';");

                List<String> names = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery("SELECT name FROM users ORDER BY name")) {
                    while (rs.next()) {
                        names.add(rs.getString("name"));
                    }
                }

                assertEquals(List.of("Adam A", "Alice A"), names,
                        "Should only return Tenant A users under Tenant A session");
            }

            // Set session variable for Tenant B in a new transaction
            conn.rollback();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant = '" + TENANT_B + "';");

                List<String> names = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery("SELECT name FROM users ORDER BY name")) {
                    while (rs.next()) {
                        names.add(rs.getString("name"));
                    }
                }

                assertEquals(List.of("Bob B"), names,
                        "Should only return Tenant B users under Tenant B session");
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName("Unit isolation: queries scoped to app.current_unit return only that branch's records")
    void rlsFiltersWorkOrdersByActiveUnit() throws Exception {
        try (Connection conn = getAppUserConnection()) {
            conn.setAutoCommit(false);

            // Tenant A + Unit A1 (Matriz)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "';");
                stmt.execute("SET LOCAL app.current_unit = '" + UNIT_A1 + "';");

                List<BigDecimal> amounts = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery("SELECT total_amount FROM work_orders ORDER BY total_amount")) {
                    while (rs.next()) {
                        amounts.add(rs.getBigDecimal("total_amount"));
                    }
                }

                assertEquals(1, amounts.size(), "Unit A1 session should only see 1 work order");
                assertEquals(new BigDecimal("500.00"), amounts.get(0));
            }

            // Tenant A + Unit A2 (Filial)
            conn.rollback();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "';");
                stmt.execute("SET LOCAL app.current_unit = '" + UNIT_A2 + "';");

                List<BigDecimal> amounts = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery("SELECT total_amount FROM work_orders ORDER BY total_amount")) {
                    while (rs.next()) {
                        amounts.add(rs.getBigDecimal("total_amount"));
                    }
                }

                assertEquals(1, amounts.size(), "Unit A2 session should only see 1 work order");
                assertEquals(new BigDecimal("750.00"), amounts.get(0));
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName("Global tenant user sees all units when app.current_unit is unset")
    void globalTenantUserSeesAllUnitsWhenUnitUnset() throws Exception {
        try (Connection conn = getAppUserConnection()) {
            conn.setAutoCommit(false);

            // Tenant A with unset unit (global headquarters role)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "';");
                // app.current_unit remains unset

                List<BigDecimal> amounts = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery("SELECT total_amount FROM work_orders ORDER BY total_amount")) {
                    while (rs.next()) {
                        amounts.add(rs.getBigDecimal("total_amount"));
                    }
                }

                assertEquals(2, amounts.size(), "Global Tenant A user should see work orders from both units");
                assertEquals(List.of(new BigDecimal("500.00"), new BigDecimal("750.00")), amounts);
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName("Fail-closed default: queries without app.current_tenant return 0 rows")
    void rlsFailsClosedWhenSessionVariableUnset() throws Exception {
        try (Connection conn = getAppUserConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM users")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt("total"), "Unset tenant must yield 0 users");
                }

                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM work_orders")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt("total"), "Unset tenant must yield 0 work orders");
                }
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName("Cross-unit insert is rejected by RLS WITH CHECK policy when unit-scoped")
    void crossUnitInsertViolatesRlsPolicy() throws Exception {
        try (Connection conn = getAppUserConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // Scope session to Tenant A + Unit A1
                stmt.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "';");
                stmt.execute("SET LOCAL app.current_unit = '" + UNIT_A1 + "';");

                // Attempt to insert work order with unit_id = Unit A2 (mismatched unit) -> must fail
                SQLException ex = assertThrows(SQLException.class, () ->
                        stmt.execute("INSERT INTO work_orders (id, tenant_id, unit_id, vehicle_id, total_amount, status) " +
                                "VALUES ('" + UUID.randomUUID() + "', '" + TENANT_A + "', '" + UNIT_A2 + "', '" + VEH_A + "', 999.00, 'PLANNED');"));

                assertTrue(ex.getMessage().contains("row-level security policy") || "42501".equals(ex.getSQLState()),
                        "Cross-unit insert must violate RLS policy. Error: " + ex.getMessage());
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName("Cross-tenant insert is rejected by RLS WITH CHECK policy")
    void crossTenantInsertViolatesRlsPolicy() throws Exception {
        try (Connection conn = getAppUserConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                // Scope session to Tenant A
                stmt.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "';");

                // Attempt to insert a user with tenant_id = Tenant B -> must fail
                SQLException ex = assertThrows(SQLException.class, () ->
                        stmt.execute("INSERT INTO users (id, tenant_id, name, email, password_hash) " +
                                "VALUES ('" + UUID.randomUUID() + "', '" + TENANT_B + "', 'Intruder', 'intruder@b.com', 'pwd');"));

                assertTrue(ex.getMessage().contains("row-level security policy") || "42501".equals(ex.getSQLState()),
                        "Cross-tenant insert must violate RLS policy. Error: " + ex.getMessage());
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName("SET LOCAL resets cleanly at transaction boundary preventing connection pool leakage")
    void setLocalResetsAfterTransaction() throws Exception {
        try (Connection conn = getAppUserConnection()) {
            conn.setAutoCommit(false);

            // Transaction 1: sets Tenant A + Unit A1
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant = '" + TENANT_A + "';");
                stmt.execute("SET LOCAL app.current_unit = '" + UNIT_A1 + "';");
            }
            conn.commit();

            // Transaction 2 on same connection: variables must be reset
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT current_setting('app.current_tenant', true) AS current_t, " +
                        "current_setting('app.current_unit', true) AS current_u")) {
                    assertTrue(rs.next());
                    String currentT = rs.getString("current_t");
                    String currentU = rs.getString("current_u");
                    assertTrue(currentT == null || currentT.isBlank(), "app.current_tenant must be unset");
                    assertTrue(currentU == null || currentU.isBlank(), "app.current_unit must be unset");
                }

                // Querying work_orders returns 0 rows because context is empty
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM work_orders")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt("total"), "No rows visible after transaction reset");
                }
            }
            conn.rollback();
        }
    }
}
